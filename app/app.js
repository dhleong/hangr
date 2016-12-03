'use strict';

const electron = require('electron'),
      fs = require('fs-extra'),
      {app, dialog, Menu} = electron,
      packageJson = require(__dirname + '/package.json'),

      DockedWindow = require('./docked-window');


// // Report crashes to atom-shell.
// require('crash-reporter').start();

const devConfigFile = __dirname + '/config.json';
var devConfig = {};
if (fs.existsSync(devConfigFile)) {
  devConfig = require(devConfigFile);
}


// const isDev = (packageJson.version.indexOf("DEV") !== -1);
const acceleratorKey = "CommandOrControl";


// Keep a global reference of the window object, if you don't, the window will
// be closed automatically when the javascript object is GCed.
var mainWindow = null;

// make sure app.getDataPath() exists
// https://github.com/oakmac/cuttle/issues/92
fs.ensureDirSync(app.getPath('userData'));


//------------------------------------------------------------------------------
// Main
//------------------------------------------------------------------------------

const versionString = "Version   " + packageJson.version + "\nDate       " + packageJson["build-date"] + "\nCommit  " + packageJson["build-commit"];


function showVersion() {
  dialog.showMessageBox({type: "info", title: "Version", buttons: ["OK"], message: versionString});
}

var fileMenu = {
  label: 'File',
  submenu: [
  {
    label: 'Quit',
    accelerator: acceleratorKey + '+Q',
    click: function ()
    {
      app.quit();
    }
  }]
};

var helpMenu = {
  label: 'Help',
  submenu: [
  {
    label: 'Version',
    click: showVersion
  }]
};

var debugMenu = {
  label: 'Debug',
  submenu: [
  {
    label: 'Toggle DevTools',
    accelerator: acceleratorKey + '+Shift+I',
    click: function ()
    {
      mainWindow.toggleDevTools();
    }
  }
  ]
};

var menuTemplate = [fileMenu, debugMenu, helpMenu];


//------------------------------------------------------------------------------
// Register IPC Calls from the Renderers
//------------------------------------------------------------------------------


//------------------------------------------------------------------------------
// Ready
//------------------------------------------------------------------------------

app.on('window-all-closed', () => {
    app.quit();
});

app.on('ready', () => {
  mainWindow = new DockedWindow();

  var menu = Menu.buildFromTemplate(menuTemplate);

  Menu.setApplicationMenu(menu);

  if (devConfig.hasOwnProperty('dev-tools') && devConfig['dev-tools'] === true) {
    mainWindow.openDevTools();
  }

});
