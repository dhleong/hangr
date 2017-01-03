
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
    beforeEach(() => {
        powerMonitor = new EventEmitter();
        connMan = new ConnectionManager();
        connMan.log = () => {}; // don't clutter

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
        powerMonitor.emit('resume');

        emitted.should.have.length(1);
        emitted[0].should.deep.equal(['got-new-events', [newConv]]);

        // we should have appended the new events:
        var cached = connMan._cachedConv('with-kaylee');
        cached.event.should.deep.equal([
            "Hey Cap'n!",
            "Cap'n Tightpants"
        ]);
    });

    it("handles events received while suspended");

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
    var pendingValues = {
    };

    function callMethod(name) {
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
            return callMethod.bind(target, prop);
        },

        set: function(target, prop, value) {
            pendingValues[prop] = value;
        }
    };
    
    return new Proxy({}, handler);
}

function Conv(id, events) {
    return {
        conversation_id: {
            id: id
        },
        event: events,
        conversation: {
            read_state: []
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
