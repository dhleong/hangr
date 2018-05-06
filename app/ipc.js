
const {urlForConvId} = require('./util');


function handlerFnToName(fnName) {
    return fnName.replace(/_/g, '-')
                 .replace('$', '!');
}

class IpcHandler {
    constructor(trayIcons, dockManager, connMan,
            showAbout, createNotification,
            systemTrayFetcher, mainWindowFetcher) {
        this.trayIcons = trayIcons;
        this.dockManager = dockManager;
        this.connMan = connMan;
        this.showAbout = showAbout;
        this.createNotification = createNotification;
        this.getSystemTray = systemTrayFetcher;
        this.getMainWindow = mainWindowFetcher;
    }

    attach(ipcMain) {
        this._handlers().forEach(nameAndHandler => {
            ipcMain.on.apply(ipcMain, nameAndHandler);
        });
    }

    _sendToMainWindow(...args) {
        var mainWindow = this.getMainWindow();
        if (mainWindow) mainWindow.send(...args);
    }

    _handlers() {
        return Object.getOwnPropertyNames(Object.getPrototypeOf(this)).filter(name => {
            return name[0] !== '_' && 
                name !== 'constructor' && 
                name !== 'attach';
        }).map(name => {
            return [handlerFnToName(name), this[name].bind(this)];
        });
    }

    /*
     * Handlers:
     *  The names are converted using handlerFnToName 
     *  before being registered in attach()
     */

    get_conversation(e, id, olderThan, eventsToFetch) {
        this.connMan.getConversation(id, olderThan, eventsToFetch);
    }

    get_entities(e, ids) {
        this.connMan.getEntities(ids);
    }

    mark_read$(e, convId, timestamp) {
        console.log(`Request: markRead(${convId})`);
        this.connMan.markRead(convId, timestamp);

        // forward to the main window
        this._sendToMainWindow('mark-read!', convId, timestamp);
    }

    notify$(e, args) {
        const notif = this.createNotification(args);
        if (!notif) return;  // not supported on this OS

        notif.on('click', () => {
            e.sender.send('notification-action', {
                id: args.id,
                type: 'click',
            });
        });

        notif.on('close', () => {
            console.log("Close");
            e.sender.send('notification-action', {
                id: args.id,
                type: 'close',
            });
        });

        if (args.hasReply) {
            notif.on('reply', (_, reply) => {
                console.log("Reply!", reply);
                e.sender.send('notification-action', {
                    id: args.id,
                    type: 'reply',
                    reply: reply,
                });
            });
        }

        notif.on('action', (_, index) => {
            console.log("Action!", index);
            e.sender.send('notification-action', {
                id: args.id,
                type: 'action',
                action: index,
            });
        });

        notif.show();
    }

    request_status(e) {
        var connMan = this.connMan;

        // TODO automate this stuff:
        if (connMan.connected) {
            // demo dev help:
            if (connMan._refreshDemo) connMan._refreshDemo();

            e.sender.send('connected');
            if (connMan.lastSelfInfo) {
                e.sender.send('self-info', connMan.lastSelfInfo);
            }

            var cachedConvs = connMan.lastConversations;
            if (cachedConvs) {
                // if it's a conversation page, filter down
                //  to the conversation they're interested in.
                //  A bit of a hack, but speeds up conversation
                //  window load time by quite a bit
                var convMatch = e.sender.getURL().match(/#\/c\/(.*)/);
                if (convMatch) {
                    var convId = convMatch[1];
                    cachedConvs = cachedConvs.filter(conv => {
                        return (conv.conversation_id && conv.conversation_id.id === convId) || 
                            (conv.conversation && 
                                conv.conversation.conversation_id && 
                                conv.conversation.conversation_id.id === convId);
                    });
                }

                e.sender.send('recent-conversations', cachedConvs);
            }
        } else {
            e.sender.send('reconnecting');
        }

        if (this.latestVersion) {
            e.sender.send('set-new-version!',
                this.latestVersion, this.latestVersionNotes);
        }
    }

    select_conv(e, convId) {
        var url = urlForConvId(convId);
        console.log("Request: select", url);
        var existing = this.dockManager.findWithUrl(url);
        if (existing) {
            existing.show();
            return;
        }

        var window = this.dockManager.open(url);
        window.on('closed', () => {
            // unfocus
            this.connMan.setFocus(convId, false);

            this._sendToMainWindow('set-focused!', convId, false);
        });
    }

    send(e, convId, imagePath, msg) {
        console.log(`Request: send(${convId}, ${imagePath}, ${JSON.stringify(msg)})`);
        // forward to the mainWindow so it can update friends list
        this._sendToMainWindow('send', convId, imagePath, msg);

        // do this last, because it modifies msg
        this.connMan.send(convId, imagePath, msg);
    }

    set_focused$(e, convId, isFocused) {
        console.log(`Request: focus(${convId}, ${isFocused})`);
        this.connMan.setFocus(convId, isFocused);

        // forward to the mainWindow so it can update friends list
        this._sendToMainWindow('set-focused!', convId, isFocused);
    }

    set_new_version$(e, version, releaseNotes) {
        this.latestVersion = version;
        this.latestVersionNotes = releaseNotes;
    }

    set_online$(e, isOnline) {
        this.connMan.setSystemOnlineState(isOnline);
    }

    set_typing$(e, convId, typingState) {
        this.connMan.setTyping(convId, typingState);
    }

    set_unread$(e, anyUnread) {
        // mainWindow controls unread status
        this.getSystemTray().setImage(
            anyUnread ? this.trayIcons.dark : this.trayIcons.light);
    }

    show_about$() {
        this.showAbout();
    }
}

module.exports = {
    IpcHandler,

    // for testing:
    handlerFnToName
};
