/**
 * Connection handling for Hangr; since we have
 *  multiple windows, we have to maintain the
 *  connection in the core process and send
 *  information to the renderers over IPC
 */
'use strict';

const EventEmitter = require('events'),
      {BrowserWindow} = require('electron'),
      Client = require('hangupsjs'),
      Promise = require('promise'),
    
      INITIAL_BACKOFF = 1000;

// Current programmatic login workflow is described here
// https://github.com/tdryer/hangups/issues/260#issuecomment-246578670
const LOGIN_URL = "https://accounts.google.com/o/oauth2/programmatic_auth?hl=en&scope=https%3A%2F%2Fwww.google.com%2Faccounts%2FOAuthLogin+https%3A%2F%2Fwww.googleapis.com%2Fauth%2Fuserinfo.email&client_id=936475272427.apps.googleusercontent.com&access_type=offline&delegated_client_id=183697946088-m3jnlsqshjhh5lbvg05k46q1k4qqtrgn.apps.googleusercontent.com&top_level_cookie=1";

const CREDS = () => {
    return {
        auth: () => {
            return new AuthFetcher().fetch();
        }
    };
};

class AuthFetcher {
    fetch() {
        return new Promise((fulfill, reject) => {
            var authBrowser = new BrowserWindow({
                width: 800,
                height: 600
            });
            authBrowser.loadURL(LOGIN_URL);
            authBrowser.webContents.on('did-finish-load', () => {
                var url = authBrowser.getURL();
                if (url.indexOf('/o/oauth2/programmatic_auth') < 0) return;

                console.log('login: auth result');
                var session = authBrowser.webContents.session;
                session.cookies.get({}, (err, cookies) => {
                    if (err) return reject(err);

                    var found = cookies.some(cookie => {
                        if (cookie.name === 'oauth_code') {
                            console.log('login: auth success!');
                            fulfill(cookie.value);
                            authBrowser.close();
                            return true;
                        }
                    });

                    if (!found) {
                        console.log('login: no oauth token');
                        reject("No oauth token");
                    }
                });
            });
        });
    }
}

class ConnectionManager extends EventEmitter {
    
    constructor() {
        super();

        this.connected = false;
        this._pendingSents = {};
        this._entities = {};
    }

    getEntities(ids) {

        // attempt to serve from local cache
        var servedFromCache = [];
        var uncached = ids.filter(id => {
            if (this._entities[id]) {
                servedFromCache.push(this._entities[id]);
                return false;
            }

            // uncached
            return true;
        });

        if (servedFromCache.length) {
            console.log(`getEntities: served ${servedFromCache.length} from cache`);
            this.emit('got-entities', servedFromCache);
        }

        if (!uncached.length) {
            console.log('getEntities: no more!');
            return;
        }

        // some new ones; fetch
        this.client.getentitybyid(uncached)
        .done(result => {
            console.log(`getEntities => ${JSON.stringify(result, null, ' ')}}`);
            if (result.error_description) {
                console.warn('getEntities:', result.error_description);
                return;
            }

            this.emit('got-entities', result.entities);

            // cache for later
            result.entities.forEach(entity => {
                this._entities[entity.id.chat_id] = entity;
            });
        }, e => {
            console.warn(`ERROR: getEntities(${JSON.stringify(ids)}})`, e);
        });
    }

    markRead(convId, timestamp) {
        this.client.updatewatermark(convId, timestamp)
        .done(result => {
            console.log(`markedRead(${convId}, ${timestamp})`, result);
            if (result.error_description) {
                return;
            }

            // update the last_read_timestamp
            var conv = this._cachedConv(convId);
            if (conv) {
                conv.conversation.self_conversation_state.self_read_state.latest_read_timestamp = timestamp;
            }
        }, e => {
            console.warn(`ERROR: markRead(${convId}, ${timestamp})`, e);
        });
    }

    /** Be connected */
    open() {
        this._backoff = INITIAL_BACKOFF;

        var client = this.client = new Client();
        client.on('connect_failed', () => {
            console.log("connection: failed; reconnecting after", this._backoff);
            setTimeout(this._reconnect.bind(this), this._backoff);
            this.emit('reconnecting in', this._backoff);
            this._backoff *= 2;
        });

        client.on('chat_message', msg => {
            var clientGeneratedId = msg.self_event_state.client_generated_id;
            if (this._pendingSents[clientGeneratedId]) {
                // we just sent this; don't bother notifying
                delete this._pendingSents[clientGeneratedId]; // cleanup
                return;
            }
            
            console.log(`*** << ${JSON.stringify(msg, null, ' ')}`);
            this._appendToConversation(msg.conversation_id.id, msg);
            this.emit('received', msg.conversation_id.id, msg);
        });

        client.on('watermark', msg => {
            console.log(`*** <<< ${JSON.stringify(msg, null, ' ')}`);
        });

        // go!
        this._reconnect();
    }

    /**
     * Attach an event listener for the given
     *  eventName that calls cb as (event, ...args)
     *  for each such event emitted
     */
    forwardEvent(eventName, cb) {
        this.on(eventName, function() {
            var args = Array.from(arguments);
            args.unshift(eventName);
            cb.apply(cb, args);
        });
    }

    send(convId, rawMsg) {
        var imageId; // TODO images?
        var otrStatus = Client.OffTheRecordStatus.ON_THE_RECORD;
        var clientGeneratedId = rawMsg.shift();
        var deliveryMedium; // TODO use the same as the original conversation?
        var messageActionType; // TODO action type?
        var builder = new Client.MessageBuilder();
        rawMsg.forEach(part => {
            var type = part[0];
            var val = part[1];
            switch (type) {
            case "linebreak":
                builder.linebreak();
                break;
            case "link":
                builder.link(part[2] || val, val);
                break;
            case "text":
                builder.text(val);
                break;
            }
        });

        this._pendingSents[clientGeneratedId] = true;
        var segments = builder.toSegments();
        this.client.sendchatmessage(convId, segments, 
            imageId, otrStatus, clientGeneratedId, deliveryMedium, messageActionType)
        .done(result => {
            console.log(`SENT(${JSON.stringify(result, null, ' ')}):`, segments);
            this._appendToConversation(convId, result.created_event);
            this.emit('sent', convId, result.created_event);
        }, e => {
            console.warn(`ERROR SENDING (${JSON.stringify(segments)}):`, e);
        });
    }

    _cachedConv(convId) {
        if (!this.lastConversations) return;
        return this.lastConversations.find(conv => 
                (conv.conversation_id && conv.conversation_id.id === convId) || 
                (conv.conversation && conv.conversation.conversation_id.id === convId));
    }

    _appendToConversation(convId, event) {
        var conv = this._cachedConv(convId);
        if (!conv) {
            console.warn("Couldn't find conversation", convId);
            return;
        }

        conv.event.push(event);
    }

    _reconnect() {
        this.connected = false;
        this.lastConversations = null;
        this.lastSelfInfo = null;
        this.client.connect(CREDS).done(
            this._connected.bind(this), 
            this._error.bind(this)
        );
    }

    _connected() {
        console.log("conn: Connected!");
        this.connected = true;
        this._backoff = INITIAL_BACKOFF;
        this.emit('connected');

        this.client.getselfinfo().then(selfInfo => {
            this.lastSelfInfo = selfInfo;
            this.emit('self-info', this.lastSelfInfo);
        }, e => console.warn("error: getselfinfo", e));
        
        this.client.syncrecentconversations().then(chats => {
            this.lastConversations = chats.conversation_state;
            this.emit('recent-conversations', this.lastConversations);
        }, e => console.warn("error: syncrecentconversations", e));
    }

    _error() {
        console.warn("conn: ERROR!", arguments);
    }
}
ConnectionManager.GLOBAL_EVENTS = [
    // events every window wants
    'connected', 
    'reconnecting',
    'self-info',
    'recent-conversations',
    'got-entities',
];
ConnectionManager.CHAT_EVENTS = [
    // events only the friends window wants
    // and a specific chat want. the first
    // argument of the event MUST be the chat id,
    // and the rest will be passed along
    'sent',
    'received',
];

module.exports = ConnectionManager;
