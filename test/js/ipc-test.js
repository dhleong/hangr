
const chai = require('chai');
const {IpcHandler, handlerFnToName} = require('../../app/ipc');

// var expect = chai.expect;
chai.should();

describe("handlerFnToName", () => {
    it("replaces _ with -", () => {
        handlerFnToName('get_entities').should.equal('get-entities');
        handlerFnToName('get_events_since').should.equal('get-events-since');
    });

    it("replaces $ with !", () => {
        handlerFnToName('mark_read$').should.equal('mark-read!');
    });
});

describe("IpcHandler", () => {

    const trayIcons = {light: 'light', dark: 'dark'};
    var dockManager;
    var connMan;
    var ipcHandler;
    var systemTray;
    var mainWindow;

    beforeEach(() => {
        dockManager = {};
        connMan = {};
        systemTray = {};
        mainWindow = {};
        ipcHandler = new IpcHandler(trayIcons, dockManager, connMan, 
            () => systemTray,
            () => mainWindow);
    });

    describe("._handlers()", () => {
        var handlers;
        var handlerNames;

        beforeEach(() => {
            handlers = ipcHandler._handlers();
            handlerNames = handlers.map(pair => pair[0]);
        });

        it("does not contain constructor or util methods", () => {
            handlerNames.should.not.contain('constructor');
            handlerNames.should.not.contain('attach');
            handlerNames.should.not.contain('_handlers');
        });

        it("contains get-entities", () => {
            handlerNames.should.contain('get-entities');
        });
    });

    describe(".attach()", () => {
        var registered = {};
        var ipcMain = {};
        beforeEach(() => {
            registered = {};
            ipcMain = {
                on: function(event, handler) {
                    registered[event] = handler;
                }
            };
            ipcHandler.attach(ipcMain);
        });

        it("does NOT register constructor, etc.", () => {
            registered.should.not.contain.key('constructor');
        });

        it("registers get_entities as get-entities", () => {
            registered.should.not.contain.key('get_entities');
            registered.should.contain.key('get-entities');
            registered['get-entities'].should.have.property('name')
                .that.has.string('get_entities');
        });
    });

    describe("handles", () => {

        var sent;
        var e;

        beforeEach(() => {
            sent = [];
            e = {
                sender: {
                    url: 'index.html#/',
                    send: function() {
                        sent.push(Array.from(arguments));
                    },
                    getURL: function() {
                        return this.url;
                    }
                }
            };
        });

        describe("request-status", () => {
            it("sends reconnecting when not connected", () => {
                ipcHandler.request_status(e);
                sent.should.deep.equal([['reconnecting']]);
            });

            it("sends connected when appropriate", () => {
                connMan.connected = true;
                ipcHandler.request_status(e);
                sent.should.deep.equal([['connected']]);
            });

            it("sends just the request conv to a /c/:id", () => {
                e.sender.url += 'c/myconv';
                connMan.connected = true;
                var myConv = {conversation: 
                                {conversation_id:
                                    {id: 'myconv'}}};
                connMan.lastConversations = [
                    {conversation: 
                        {conversation_id:
                            {id: 'otherconv1'}}},
                    myConv,
                    {conversation: 
                        {conversation_id:
                            {id: 'otherconv2'}}},
                ];
                ipcHandler.request_status(e);
                sent.should.have.length(2);
                sent[0].should.deep.equal(['connected']);
                sent[1].should.deep.equal([
                    'recent-conversations',
                    [myConv]
                ]);
            });
        });
    });
});
