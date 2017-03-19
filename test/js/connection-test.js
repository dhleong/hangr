
const chai = require('chai');
const ConnectionManager = require('../../app/connection');
const EventEmitter = require('events');

// var expect = chai.expect;
chai.should();

const INITIAL_CONVERSATIONS = [
    Conv("with-kaylee", [
        "Hey Cap'n!"
    ])
];

describe("ConnectionManager", () => {
    var connMan;
    var powerMonitor;
    var emitted = [];
    var now = 9001;
    beforeEach(() => {
        emitted = [];
        
        powerMonitor = new EventEmitter();
        connMan = new ConnectionManager();
        connMan.log = () => {}; // don't clutter
        connMan.now = () => now;

        connMan.client = new FakeClient();
        connMan._getPowerMonitor = () => powerMonitor;

        // track emitted events:
        var oldEmit = connMan.emit;
        connMan.emit = (eventName, ...args) => {
            emitted.push([eventName, ...args]);
            oldEmit.call(connMan, eventName, ...args);
        };

        // init some mocked values
        connMan.client.syncrecentconversations = {
            conversation_state: INITIAL_CONVERSATIONS
        };

        connMan.bindToClient(connMan.client);
        connMan._connected();

        // sanity checks
        emitted[0].should.deep.equal(['connected']);
        emitted.should.have.length(2);
        emitted[1].should.deep.equal(['recent-conversations', INITIAL_CONVERSATIONS]);

        // clean up for nicer testing
        emitted = [];
    });

    it("requests missing events on resume", () => {
        var newConv = Conv('with-kaylee', [
            "Cap'n Tightpants"
        ]);
        connMan.client.syncallnewevents = {conversation_state: [ newConv ]};
        powerMonitor.emit('suspend');

        now = 10000; // time passes, before...
        powerMonitor.emit('resume');

        // we should have sync'd with the time of suspend, 9001
        connMan.client.should.have.called('syncallnewevents', 9001);
        emitted.should.have.length(1);
        emitted[0].should.deep.equal(['got-new-events', [newConv]]);

        // we should have appended the new events:
        var cached = connMan._cachedConv('with-kaylee');
        cached.event.should.deep.equal([
            "Hey Cap'n!",
            "Cap'n Tightpants"
        ]);
    });

    it("handles events received while suspended", () => {
        powerMonitor.emit('suspend');

        now = 10000; // time passes, before...
        connMan.client.emit('chat_message', {
            conversation_id: {id: 'with-kaylee'},
            self_event_state: {}
        });

        now = 11000; // and then... finally we resume
        powerMonitor.emit('resume');

        // we should sync "since the most recent message"
        connMan.client.should.have.called('syncallnewevents', 10000);
    });

    describe("Events:", () => {
        it("`client_conversation` *after* chat_message for new conversation emits recent-conversations", () => {
            var msg, conv;
            connMan.client.emit('chat_message', (msg = {
                conversation_id: {id: 'with-zoe'},
                self_event_state: {}
            }));

            connMan.client.emit('client_conversation', (conv = {
                conversation_id: {id: 'with-zoe'},
                read_state: { }
            }));

            emitted.should.have.length(2);
            emitted[0].should.deep.equal(['recent-conversations', [
                {
                    conversation_id: conv.conversation_id,
                    conversation: conv,
                    event: [ ]
                }
            ]]);

            emitted[1].should.deep.equal(['received', 'with-zoe', msg]);
        });

        it("`watermark` for other just updates cache", () => {
            connMan.client.emit('watermark', {
                conversation_id: {id: 'with-kaylee'},
                participant_id: PID('itskaylee'),
                latest_read_timestamp: 42
            });

            var cached = connMan._cachedConv('with-kaylee');
            cached.conversation.self_conversation_state.self_read_state
            .latest_read_timestamp.should.equal(0);

            cached.conversation.read_state[0].last_read_timestamp
            .should.equal(42);
        });

        it("`watermark` for self updates self_ cache", () => {
            connMan.client.emit('watermark', {
                conversation_id: {id: 'with-kaylee'},
                participant_id: PID('mreynolds'),
                latest_read_timestamp: 42
            });

            var cached = connMan._cachedConv('with-kaylee');
            cached.conversation.self_conversation_state.self_read_state
            .latest_read_timestamp.should.equal(42);
        });
    });
});

/**
 * Mockable Client object constructor; use as new FakeClient();
 * To mock a call, you assign a value to the name of the function, eg:
 *
 *    client.getselfinfo = {id: {etc}}
 *
 * The value set will be returned in the promise for
 *  the next call of that function
 */
function FakeClient() {
    var pendingValues = { };
    var calls = { };
    var events = new EventEmitter();

    function callMethod(name, ...args) {
        calls[name] = args;
        
        const pendingValue = pendingValues[name];
        delete pendingValues[name];
        if (pendingValue) {
            return new ImmediatePromise(resolve => {
                resolve(pendingValue);
            });
        } else {
            // never resolve
            return new ImmediatePromise(() => {});
        }
    }

    var handler = {

        // NOTE: client "get"s are all for method calls
        get: function(target, prop) {
            if (prop === 'should') {
                // exception: 'should' assertions
                return {
                    have: {
                        called: function(fnName, ...expectedArgs) {
                            calls.should.contain.key(fnName);
                            calls[fnName].should.deep.equal(expectedArgs);
                        }
                    }
                };
            }

            if (events[prop]) {
                return events[prop].bind(events);
            }
            
            return callMethod.bind(target, prop);
        },

        set: function(target, prop, value) {
            pendingValues[prop] = value;
        }
    };
    
    return new Proxy({}, handler);
}

function PID(chat_id) {
    return {
        chat_id: chat_id,
        gaia_id: chat_id
    };
}

function Conv(id, events) {
    return {
        conversation_id: {
            id: id
        },
        event: events,
        conversation: {
            read_state: [
                {
                    participant_id: PID('itskaylee'),
                    last_read_timestamp: 0
                }
            ],

            self_conversation_state: {
                self_read_state: {
                    latest_read_timestamp: 0,
                    participant_id: PID("mreynolds")
                }
            }
        },
    };
}

class ImmediatePromise {
    constructor(resolver) {
        this._resolver = resolver;

        // not exactly, but close enough
        this.done = this.then;
    }

    then(onResolve, onReject) {
        this._resolver(onResolve, onReject);
    }
}
