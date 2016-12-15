'use strict';

var electron;
try {
    electron = require('electron');
} catch (e) {
    // unit test mode
    electron = {};
}
const {BrowserWindow} = electron;

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

        window.on('hide', () => {
            window._hidden = true;
            this._animateOut(window);
        });
        window.on('closed', this._animateOut.bind(this, window));
        window.on('show', () => {
            if (!window._hidden) return;
            window._hidden = false;

            // ensure the index isn't out of range if we're
            // showing it out of the order we hid it in
            window._index = Math.min(
                window._index, 
                this._windows.length
            );
            this._animateIn(window);
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

    open(url) {
        // NOTE: Using a constructor as if it were a function
        //  leaves a bad smell, but it's well-contained here;
        //  at some point we should probably refactor the stuff
        //  out of constructor and stop exporting DockedWindow
        new DockedWindow(url);
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

    _animateOut(window) {
        for (var i=window._index + 1; i < this._windows.length; i++) {
            var win = this._windows[i];

            // shift it back
            --win._index;

            // re-position it
            this.position(win, 'out');
        }

        this._windows.splice(window._index, 1);
    }

    _animateIn(window) {
        if (this._windows.indexOf(window) !== -1) {
            throw new Error(`IllegalState: _animateIn(${window.url}) that's already in _windows`);
        }

        this.position(window, 'out');

        for (var i=window._index; i < this._windows.length; i++) {
            var win = this._windows[i];

            // shift it back
            ++win._index;

            // re-position it
            this.position(win, 'out');
        }

        this._windows.splice(window._index, 0, window);
    }
}

const Manager = new DockManager();

const DELEGATED_METHODS = [
    'on', 'once', 'removeListener', 'removeAllListeners',
    'close', 'focus', 'hide', 'show',
    'getURL', 'setPosition', 
    'openDevTools', 'toggleDevTools',
];
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

        // delegate some stuff
        DELEGATED_METHODS.forEach(methodName => {
            this[methodName] = win[methodName].bind(win);
        });

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
}

module.exports = {
    DockedWindow,
    dockManager: Manager,

    // constructor, for testing
    DockManager,
};
