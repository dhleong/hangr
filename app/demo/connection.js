
const fs = require('fs'),
      osPath = require('path'),
      EventEmitter = require('events'),

      ConnectionManager = require('../connection');

function readJson(path) {
    var fullPath = osPath.join(__dirname, path);
    return JSON.parse(fs.readFileSync(fullPath));
}

class DemoConnectionManager extends EventEmitter {

    open() {
        this._refreshDemo();
        this.connected = true;
        setTimeout(() => {
            this.emit('connected');
            setTimeout(() => {
                this.emit('self-info', this.lastSelfInfo);
                this.emit('recent-conversations', this.lastConversations);
            }, 100);
        }, 1000);
    }

    forwardEvent(eventName, cb) {
        this.on(eventName, function() {
            var args = Array.from(arguments);
            args.unshift(eventName);
            cb.apply(cb, args);
        }); 
    }

    getEntities() {
        // TODO
    }

    markRead() {}
    setFocus() {}

    _refreshDemo() {
        this.lastConversations = readJson('./data/recent-conversations.json');
        this.lastSelfInfo = readJson('./data/self-info.json');
    }
}

DemoConnectionManager.GLOBAL_EVENTS = ConnectionManager.GLOBAL_EVENTS;
DemoConnectionManager.CHAT_EVENTS = ConnectionManager.CHAT_EVENTS;
DemoConnectionManager.CHAT_ONLY_EVENTS = ConnectionManager.CHAT_ONLY_EVENTS;
module.exports = DemoConnectionManager;
