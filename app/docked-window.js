'use strict';

const electron = require('electron'),
      {BrowserWindow} = electron;

const WindowDimens = {
    w: 240,
    h: 420,
    space: 20,
};

// utils

function screenSize() {
    return electron.screen.getPrimaryDisplay().size;
}

var Anchor = {
    Start: {
        position: (index) => {
            return (index + 1) * WindowDimens.space + index * WindowDimens.w;
        }
    },

    End: {
        position: (index) => {
            const {width} = screenSize();
            return width - WindowDimens.w - Anchor.Start.position(index);
        }
    }
};


class DockManager {
    constructor() {
        this._anchor = Anchor.End;
        this._windows = [];
    }

    adopt(window) {
        var index = this._windows.length;
        this._windows.push(window);
        window._index = index;
        this.position(window, 'in');

        window.on('closed', () => {
            for (var i=window._index + 1; i < this._windows.length; i++) {
                var win = this._windows[i];

                // shift it back
                --win._index;

                // re-position it
                this.position(win, 'out');
            }

            this._windows.splice(window._index, 1);
        });
    }

    dispatch(/* event, ...args */) {
        var args = Array.from(arguments);
        this._windows.forEach(window => {
            window.send.apply(window, args);
        });
    }

    findActive() {
        var focused = BrowserWindow.getFocusedWindow();
        return this._windows.find(win => {
            return win.win === focused;
        });
    }

    findWithUrl(url) {
        return this._windows.find(win => {
            return win.url === url;
        });
    }

    position(window, animate) {
        var screenHeight = screenSize().height;
        var x = this._anchor.position(window._index);
        var y = screenHeight - window.height;

        // special case: when animating in, we like a nice slide
        if (animate === 'in') {
            window.setPosition(x, screenHeight, false);
            setTimeout(() => {
                window.setPosition(x, y, true);
            }, 50);
            return;
        }

        window.setPosition(x, y, !!animate);
    }
}

const Manager = new DockManager();

class DockedWindow {
    constructor(url = '/') {
        this.url = url;

        // Create the browser window.
        var win = this.win = new BrowserWindow({
            titleBarStyle: 'hidden-inset',

            width: WindowDimens.w,
            height: WindowDimens.h,
            maxWidth: WindowDimens.w,
            minWidth: WindowDimens.w,
            minHeight: WindowDimens.h,

            moveable: false, // for now; we'd like drag and drop, probably
            maximizable: false,
            fullscreen: false,

            fullscreenable: false,
            alwaysOnTop: true,
            skipTaskBar: true,
        });

        // and load the index.html of the app.
        win.loadURL('file://' + __dirname + '/index.html#' + url);

        // delegate some event stuff
        this.on = win.on.bind(win);
        this.once = win.once.bind(win);
        this.removeListener = win.removeListener.bind(win);
        this.removeAllListeners = win.removeAllListeners.bind(win);

        // LAST:
        Manager.adopt(this);
    }

    get height() {
        return this.win.getSize()[1];
    }

    get width() {
        return WindowDimens.w;
    }

    /** Shortcut to send an IPC event to the browser */
    send(/* event, ... args */) {
        var contents = this.win.webContents;
        contents.send.apply(contents, Array.from(arguments));
    }

    getURL() {
        return this.win.getURL();
    }

    setPosition(x, y, animate) {
        this.win.setPosition(x, y, animate);
    }

    openDevTools() {
        this.win.openDevTools();
    }

    toggleDevTools() {
        this.win.toggleDevTools();
    }
}

module.exports = {
    DockedWindow,
    dockManager: Manager,
};
