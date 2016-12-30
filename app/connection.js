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
      {parsePresencePacket} = require('./util'),
    
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

        client.on('hangout_event', msg => {
            console.log(`*** <<H ${JSON.stringify(msg, null, ' ')}`);
            this._appendToConversation(msg.conversation_id.id, msg);
            this.emit('received', msg.conversation_id.id, msg);
        });

        client.on('focus', msg => {
            // TODO remember focus states for if we close and re-open a conversation?
            // TODO Also, update the latest-read-timestamp for this user
            console.log(`*** <<F ${JSON.stringify(msg, null, ' ')}`);
            delete msg._header;
            this.emit('focus', msg.conversation_id.id, msg);
        });

        client.on('presence', msg => {
            console.log(`*** <<P ${JSON.stringify(msg)}`);
            const asJson = parsePresencePacket(msg);
            console.log(`     -> ${JSON.stringify(asJson, null, ' ')}`);
        });

        client.on('typing', msg => {
            console.log(`*** <<T ${JSON.stringify(msg)}`);
            delete msg._header;
            this.emit('typing', msg.conversation_id.id, msg);
        });

        client.on('watermark', msg => {
            var convId = msg.conversation_id.id;
            this.emit('watermark', convId, msg);

            // update any cached value
            var cached = this._cachedConv(convId);
            if (cached) {
                var readStates = cached.conversation.read_state;
                readStates.some(state => {
                    if (state.participant_id.chat_id === msg.participant_id.chat_id) {
                        // update the timestamp
                        state.last_read_timestamp = msg.latest_read_timestamp;
                        return true; // short-circuit some()
                    }
                });
            }
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

    /**
     * Send a message, optionally including an image
     * @params imagePath Path to an image to attach. If you
     *  don't need an image, pass NULL (DO NOT omit)
     */
    send(convId, imagePath, rawMsg) {
        if (!imagePath) {
            console.log("send: No image");
            return this._send(convId, undefined, rawMsg);
        } else {
            // upload, then send
            console.log("send: Uploading image", imagePath);
            return this.client.uploadimage(imagePath)
            .then(imageId => {
                console.log("send: Uploaded image ->", imageId);
                return this._send(convId, imageId, rawMsg);
            });
        }
    }

    /**
     * Actually do the sending
     * @params imageId An image id retrieved via client.uploadimage,
     * or undefined if no imageId
     */
    _send(convId, imageId, rawMsg) {
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

    setFocus(convId, isFocused) {
        var focusType = isFocused ? Client.FocusStatus.FOCUSED : Client.FocusStatus.UNFOCUSED;
        var timeout = 60; // we can be longer since we get explicit events on desktop
        this.client.setfocus(convId, focusType, timeout)
        .done(result => {
            // if we get here, this path SHOULD exist, but be safe...
            var status = result && result.response_header && result.response_header.status;
            console.log(`setFocus(${convId}, ${isFocused})`, status);
        }, e => {
            console.warn(`ERROR: setFocus(${convId}, ${isFocused})`, e);
        });
    }

    /**
     * Set typingState for a conversation
     * @param typingState One of "typing," "paused," or "stopped"
     */
    setTyping(convId, typingState) {
        var status = Client.TypingStatus[typingState.toUpperCase()];
        this.client.settyping(convId, status)
        .catch(e => {
            console.warn(`ERROR: setTyping(${convId}, ${status})`, e);
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
    'focus',
    'received',
    'sent',
    'typing',
    'watermark',
];

module.exports = ConnectionManager;
