/**
 * Connection handling for Hangr; since we have
 *  multiple windows, we have to maintain the
 *  connection in the core process and send
 *  information to the renderers over IPC
 */
'use strict';

const EventEmitter = require('events'),
      Client = require('hangupsjs'),
      Promise = require('promise'),
      {parsePresencePacket, throttle} = require('./util'),

      INITIAL_BACKOFF = 1000,

      ERROR_NOT_LOGGEDIN = "No oauth token";

var electron, BrowserWindow;
try {
    electron = require('electron'),
    {BrowserWindow} = electron;
} catch (e) {
    // should be only in tests
}

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

                    cookies.some(cookie => {
                        if (cookie.name === 'oauth_code') {
                            console.log('login: auth success!');
                            fulfill(cookie.value);
                            authBrowser.close();
                            return true;
                        }
                    });

                    // FIXME: right now this happens when we first
                    // open the login window. We probably need to
                    // fix the url in the `if` above to be more selective
                    // if (!found) {
                    //     console.log('login: no oauth token');
                    //     reject(ERROR_NOT_LOGGEDIN);
                    // }
                });
            });
        });
    }
}

class ConnectionManager extends EventEmitter {

    constructor() {
        super();

        this.connected = false;
        this.client = null; // created in open()
        this._pendingSents = {};
        this._entities = {};

        const activeDuration = 5 * 60;
        this.notifyActivity = throttle(
            this._setActive.bind(this, true, activeDuration),
            activeDuration * 1000);
    }

    log(...args) {
        console.log("" + new Date(), ...args);
    }

    now() {
        return Date.now();
    }

    logout() {
        return this.client.logout();
    }

    /**
     * Get events from a conversation that are
     * older than `olderThan`
     * @param olderThan A timestamp in millis
     * @param eventsToFetch Optional; number of chat
     *  events to fetch (can be a small number like 1
     *  if you just want metadata, but CANNOT be zero)
     */
    getConversation(convId, olderThan, eventsToFetch=50) {
        const includeMeta = true;
        this.client.getconversation(convId, olderThan, eventsToFetch, includeMeta)
        .done(resp => {
            if (!resp.conversation_state) {
                console.warn(`ERROR: getConversation(${convId}, ${olderThan})`, resp);
                return;
            }

            var conv = resp.conversation_state;
            this.emit('got-conversation', conv.conversation_id.id, conv);

            // save read_states and insert new events
            this._mergeConversation(conv);
        }, e => {
            console.warn(`ERROR: getConversation(${convId}, ${olderThan})`, e);
        });
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

    getEventsSince(timestampSince) {
        this.client.syncallnewevents(timestampSince)
        .done(result => {
            this.log(`gotEventsSince: ${JSON.stringify(result, null, ' ')}`);
            if (result.conversation_state) {
                // update cache
                result.conversation_state.forEach(this._mergeNewConversation.bind(this));

                this.emit('got-new-events', result.conversation_state);
            }
        }, e => {
            console.warn(`ERROR: getEventsSince(${timestampSince})`, e);
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
        this.bindToClient(client);


        // go!
        this._reconnect();
    }

    // NOTE: separated from open() for testing
    bindToClient(client) {
        client.on('connect_failed', reason => {
            if (reason === ERROR_NOT_LOGGEDIN) {
                this.log("conn: failed; not logged in");
                return;
            }

            this.log("conn: failed; reconnecting after", this._backoff);
            setTimeout(this._reconnect.bind(this), this._backoff);
            this.emit('reconnecting', this._backoff);
            this._backoff *= 2;
            this._disableMonitoring();
        });

        client.on('client_conversation', msg => {
            var cached = this._cachedConv(msg.conversation_id.id);
            if (cached) {
                if (!cached.conversation) {
                    // we had some pending messages; pop off the most recent
                    // so we can send it as "received" and trigger a notification
                    cached.conversation = msg;
                    var received = cached.event.splice(-1, 1)[0];

                    // do the json stringify-parse dance to make sure
                    // clients don't receive the version with the event
                    // pushed back on, done below
                    this.emit('recent-conversations',
                        JSON.parse(JSON.stringify([cached])));

                    this.emit('received', msg.conversation_id.id, received);
                    cached.event.push(received);
                } else {
                    // TODO do we need to update meta or something?
                    console.log('*** client_conversation', msg);
                    cached.conversation = msg;

                    // notify
                    this.emit('recent-conversations', [cached]);
                }
            } else {
                // totally new; save the data
                console.log('!!! client_conversation', msg);
                var conversation = {
                    conversation: msg,
                    conversation_id: msg.conversation_id,
                    event: [],
                };
                this.lastConversations.push(conversation);
            }
        });

        client.on('chat_message', msg => {
            var clientGeneratedId = msg.self_event_state.client_generated_id;
            if (this._pendingSents[clientGeneratedId]) {
                // we just sent this; don't bother notifying
                delete this._pendingSents[clientGeneratedId]; // cleanup
                return;
            }

            this.log(`*** << ${JSON.stringify(msg, null, ' ')}`);
            if (this._appendToConversation(msg.conversation_id.id, msg)) {
                this.emit('received', msg.conversation_id.id, msg);
            } else if (this.lastConversations) {
                // store as pending; we should get a client_conversation soon
                this.lastConversations.push({
                    conversation_id: msg.conversation_id,
                    event: [msg]
                });
            }
        });

        client.on('delete', msg => {
            var id = msg[0][0];
            console.log(`*** <<D ${JSON.stringify(msg)} -> ${id}`);
            // TODO delete from cache
            this.emit('delete', id, id);
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

                var selfReadState = cached.conversation.self_conversation_state.self_read_state;
                if (selfReadState.participant_id.chat_id === msg.participant_id.chat_id) {
                    selfReadState.latest_read_timestamp = msg.latest_read_timestamp;
                }
            }
        });
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
        this.notifyActivity();
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
        this.notifyActivity();
    }

    _cachedConv(convId) {
        if (!this.lastConversations) return;
        return this.lastConversations.find(conv =>
                (conv.conversation_id && conv.conversation_id.id === convId) ||
                (conv.conversation
                    && conv.conversation.conversation_id
                    && conv.conversation.conversation_id.id === convId));
    }

    _appendToConversation(convId, event) {
        var conv = this._cachedConv(convId);
        if (!conv) {
            console.warn("_appendTo: Couldn't find conversation", convId);
            return false;
        }

        conv.event.push(event);
        return true;
    }

    /** 
     * "Merge" newConv into any existing, cached conv
     *  with the same id. In this case, that means
     *  inserting newConv's `event` list and replacing
     *  oldConv's read_state list with newConv's
     */
    _mergeConversation(newConv) {
        var convId = newConv.conversation_id.id;
        var oldConv = this._cachedConv(convId);
        if (!oldConv) {
            console.warn("_merge: Couldn't find conversation", convId);
            return;
        }

        oldConv.conversation.read_state = newConv.conversation.read_state;
        oldConv.event = newConv.event.concat(oldConv.event);
    }

    /**
     * Merge "new" events into any cached conversation;
     *  this differs from _mergeConversation in that
     *  newConv will contain events received while
     *  suspended or without net, so the events should
     *  be appended, not inserted.
     * TODO: simplify this stuff, possibly rename
     */
    _mergeNewConversation(newConv) {
        var convId = newConv.conversation_id.id;
        var oldConv = this._cachedConv(convId);
        if (!oldConv) {
            console.warn("_mergeNew: Couldn't find conversation", convId);
            return;
        }

        oldConv.conversation.read_state = newConv.conversation.read_state;
        oldConv.conversation.self_conversation_state = 
            newConv.conversation.self_conversation_state;
        oldConv.event = oldConv.event.concat(newConv.event);
    }

    _reconnect() {
        this.log("conn: Reconnect...");
        this.connected = false;
        this.lastConversations = null;
        this.lastSelfInfo = null;
        this.client.connect(CREDS).done(
            this._connected.bind(this),
            this._error.bind(this)
        );
    }

    _connected() {
        this.log("conn: Connected!");
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

        this._enableMonitoring();
    }

    _disableMonitoring() {
        const {powerMonitor} = electron;
        powerMonitor.removeAllListeners('suspend');
        powerMonitor.removeAllListeners('resume');
    }

    _enableMonitoring() {
        const powerMonitor = this._getPowerMonitor();
        
        var self = this;
        var onReceiveWhileSuspended = function onReceiveWhileSuspended() {
            self._suspendedAt = this.now();
        };
        powerMonitor.on('suspend', () => {
            this.log("SUSPEND");
            this._suspendedAt = this.now();
            this.notifyActivity.clear(); // clear any pending call
            this._setActive(false);
            this.on('received', onReceiveWhileSuspended);
        });
        powerMonitor.on('resume', () => {
            this.log("RESUME");
            this.removeListener('received', onReceiveWhileSuspended);
            this.notifyActivity();
            if (this._suspendedAt) {
                this.getEventsSince(this._suspendedAt);
                this._suspendedAt = undefined;
            }
        });

        this._setActive(true);
    }

    _getPowerMonitor() {
        const {powerMonitor} = electron;
        return powerMonitor;
    }

    _error(err) {
        console.warn("conn: ERROR!", err);
    }

    _setActive(isActive) {
        this.client.setactiveclient(isActive, isActive ? 5 * 60 : 10)
        .done(resp => {
            if (resp.response_header.status !== 'OK') {
                console.log("ERROR: setactive", resp);
            } else {
                this.log(`setActive(${isActive})`);
            }
        }, e => {
            console.log("ERROR: setactive", e);
        });
    }
}

ConnectionManager.GLOBAL_EVENTS = [
    // events every window wants
    'connected', 
    'reconnecting',
    'self-info',
    'recent-conversations',
    'got-entities',
    'got-new-events',
];
ConnectionManager.CHAT_EVENTS = [
    // events only the friends window 
    // and a specific chat want. the first
    // argument of the event MUST be the chat id,
    // and the rest will be passed along
    'delete',
    'focus',
    'received',
    'sent',
    'typing',
    'watermark',
];
ConnectionManager.CHAT_ONLY_EVENTS = [
    // events only a specific chat want.
    // works like CHAT_EVENTS, but does not
    // get delivered to the friends window
    'got-conversation',
];

module.exports = ConnectionManager;
