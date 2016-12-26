
const {urlForConvId} = require('./util');


function handlerFnToName(fnName) {
    return fnName.replace('_', '-')
                 .replace('$', '!');
}

class IpcHandler {
    constructor(trayIcons, dockManager, connMan, 
            systemTrayFetcher, mainWindowFetcher) {
        this.trayIcons = trayIcons;
        this.dockManager = dockManager;
        this.connMan = connMan;
        this.getSystemTray = systemTrayFetcher;
        this.getMainWindow = mainWindowFetcher;
    }

    attach(ipcMain) {
        this._handlers().forEach(nameAndHandler => {
            ipcMain.on.apply(ipcMain, nameAndHandler);
        });
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

    get_entities(e, ids) {
        this.connMan.getEntities(ids);
    }

    mark_read$(e, convId, timestamp) {
        this.connMan.markRead(convId, timestamp);
    }

    request_status(e) {
        var connMan = this.connMan;

        // TODO automate this stuff:
        if (connMan.connected) {
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
        });
    }

    send(e, convId, imagePath, msg) {
        console.log(`Request: send(${convId}, ${imagePath}, ${JSON.stringify(msg)})`);
        // forward to the mainWindow so it can update friends list
        var mainWindow = this.getMainWindow();
        if (mainWindow) mainWindow.send('send', convId, imagePath, msg);

        // do this last, because it modifies msg
        this.connMan.send(convId, imagePath, msg);
    }

    set_focused$(e, convId, isFocused) {
        console.log(`Request: focus(${convId}, ${isFocused})`);
        this.connMan.setFocus(convId, isFocused);
    }

    set_typing$(e, convId, typingState) {
        this.connMan.setTyping(convId, typingState);
    }

    set_unread$(e, anyUnread) {
        // mainWindow controls unread status
        this.getSystemTray().setImage(
            anyUnread ? this.trayIcons.dark : this.trayIcons.light);
    }
}

module.exports = {
    IpcHandler,

    // for testing:
    handlerFnToName
};
