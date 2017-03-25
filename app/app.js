'use strict';

const electron = require('electron'),
      fs = require('fs-extra'),
      path = require('path'),
      {app, BrowserWindow, ipcMain, Menu, Tray} = electron,
      // packageJson = require(__dirname + '/package.json'),

      {urlForConvId} = require('./util'),
      {IpcHandler} = require('./ipc'),
      {dockManager, DockedWindow} = require('./docked-window'),
      isDemo = process.argv.length &&
        process.argv[process.argv.length - 1] === 'demo',
      ConnectionManager = isDemo
        ? require('./demo/connection')
        : require('./connection'),

      connMan = new ConnectionManager();

const devConfigFile = __dirname + '/config.json';
var devConfig = {};
if (fs.existsSync(devConfigFile)) {
    devConfig = require(devConfigFile);
}

const trayIcons = {
    light: __dirname + '/img/logo-light.png',
    dark: __dirname + '/img/logo-dark.png',
};

// Keep a global reference of the window object, if you don't, the window will
// be closed automatically when the javascript object is GCed.
var mainWindow = null;
var systemTray = null;

// make sure app.getDataPath() exists
// https://github.com/oakmac/cuttle/issues/92
fs.ensureDirSync(app.getPath('userData'));


//------------------------------------------------------------------------------
// Main
//------------------------------------------------------------------------------

/** cleanup before we can quit */
function preQuit() {
    if (mainWindow) {
        mainWindow.removeAllListeners('close');
    }
}

function quit() {
    preQuit();
    mainWindow.close();
    app.quit();
}

function logout() {
    connMan.logout().then(() => {
        var cookiesFile = path.join(app.getPath('userData'), 'Cookies');
        if (fs.existsSync(cookiesFile)) {
            fs.unlinkSync(cookiesFile);
        }

        quit();
    });
}

// const versionString =
//     `Version   ${packageJson.version}\n` +
//     `Date      ${packageJson['build-date']}\n` +
//     `Commit    ${packageJson['build-commit']}`;

function showVersion() {
    // dialog.showMessageBox({
    //     type: "info",
    //     title: "Version",
    //     buttons: ["OK"],
    //     message: versionString
    // });

    var win = new BrowserWindow({
        width: 400,
        height: 320,
        center: true,
        resizable: false,
        minimizable: false,
        maximizable: false,
        fullscreenable: false,
        title: "About Hangr",
        show: false,
    });
    win.loadURL(`file://${__dirname}/index.html#/about`);
    win.once('ready-to-show', () => {
        win.show();
    });
}

const fileMenu = {
    label: 'File',
    submenu: [
        {
            label: 'Close Window',
            accelerator: 'CmdOrCtrl+W',
            click: function() {
                var active = dockManager.findActive();
                if (active) {
                    active.close();
                }
            }
        },
        {
            label: 'Quit',
            accelerator: 'CmdOrCtrl+Q',
            click: quit
        }
    ]
};

const editMenu = {
    label: 'Edit',
    submenu: [
        { label: "Undo", accelerator: 'CmdOrCtrl+Z', role: 'undo' },
        { label: "Redo", accelerator: 'Shift+CmdOrCtrl+Z', role: 'redo' },
        { type: "separator" },
        { label: "Cut", accelerator: 'CmdOrCtrl+X', role: 'cut' },
        { label: "Copy", accelerator: 'CmdOrCtrl+C', role: 'copy' },
        { label: "Paste", accelerator: 'CmdOrCtrl+V', role: 'pasteandmatchstyle' },
        { label: "Select All", accelerator: 'CmdOrCtrl+A', role: 'selectall' }
    ]
};

const helpMenu = {
    label: 'Help',
    submenu: [
        {
            label: 'Version',
            click: showVersion
        }
    ]
};

const debugMenu = {
    label: 'Debug',
    submenu: [
        {
            label: 'Toggle DevTools',
            accelerator: 'CmdOrCtrl+Shift+I',
            click: function() {
                var active = dockManager.findActive();
                if (active) active.toggleDevTools();
                else if (mainWindow) mainWindow.toggleDevTools();
            }
        }
    ]
};

const menuTemplate = [fileMenu, editMenu, debugMenu, helpMenu];


const trayContextMenu = [
    {
        label: 'About Hangr',
        click: showVersion
    },
    { type: 'separator' },
    {
        label: 'Logout',
        click: logout
    },
    { type: 'separator' },
    {
        label: 'Quit',
        click: quit
    }
];

//------------------------------------------------------------------------------
// Util
//------------------------------------------------------------------------------

function showMainWindow() {
    mainWindow = new DockedWindow();
    mainWindow.on('close', e => {
        console.log('"Closing" main window');
        e.preventDefault();
        mainWindow.hide();

        connMan.notifyActivity();
    });
    mainWindow.on('closed', () => {
        mainWindow = null;
    });
}

//------------------------------------------------------------------------------
// Register IPC Calls from the Renderers
//------------------------------------------------------------------------------

const ipcHandler = new IpcHandler(trayIcons, dockManager, connMan, 
    () => systemTray,
    () => mainWindow);
ipcHandler.attach(ipcMain);

//------------------------------------------------------------------------------
// Ready
//------------------------------------------------------------------------------

// hook into the event so we can quit more gracefully at cli (with ctrl+c)
app.on('before-quit', preQuit);

app.on('ready', () => {
    // show the main window
    showMainWindow();

    Menu.setApplicationMenu(
        Menu.buildFromTemplate(menuTemplate));

    if (devConfig['dev-tools'] === true) {
        mainWindow.openDevTools();
    }

    // forward events to the client
    // TODO: this could be cleaner:
    ConnectionManager.GLOBAL_EVENTS.forEach(event => {
        connMan.forwardEvent(event, dockManager.dispatch.bind(dockManager));
    });

    ConnectionManager.CHAT_EVENTS.forEach(event => {
        connMan.forwardEvent(event, function(e, convId, ...args) {
            if (mainWindow) mainWindow.send(event, ...args);
            var convWin = dockManager.findWithUrl(urlForConvId(convId));
            if (convWin) convWin.send(event, ...args);
        });
    });

    ConnectionManager.CHAT_ONLY_EVENTS.forEach(event => {
        connMan.forwardEvent(event, function(e, convId, ...args) {
            var convWin = dockManager.findWithUrl(urlForConvId(convId));
            if (convWin) convWin.send(event, ...args);
        });
    });

    // begin connecting immediately
    connMan.open();

    // TODO 'click' doesn't work consistently on linux,
    // so we should set the context menu for that; on OSX,
    // though, we want to be able to just click the system
    // menu icon to open the friends list
    const trayMenu = Menu.buildFromTemplate(trayContextMenu);
    systemTray = new Tray(trayIcons.light);
    systemTray.on('click', (e) => {
        if (e.altKey) {
            systemTray.popUpContextMenu(trayMenu);
            return;
        }

        if (mainWindow) {
            mainWindow.show();
        } else {
            showMainWindow();
        }

        connMan.notifyActivity();
    });
    systemTray.on('right-click', () => {
        systemTray.popUpContextMenu(trayMenu);
    });

    // hide the dock icon; we have a tray icon!
    app.dock.hide();
});
