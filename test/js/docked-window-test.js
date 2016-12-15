
const chai = require('chai');
const EventEmitter = require('events');
const {DockManager} = require('../../app/docked-window');

var expect = chai.expect;
chai.should();

class FakeWindow extends EventEmitter {
    constructor(url) {
        super();
        this.url = url;
    }
    close() { this.emit('closed'); }

    hide() { this.emit('hide'); }

    show() { this.emit('show'); }
}

describe("DockManager", () => {

    var dockManager;

    beforeEach(() => {
        dockManager = new DockManager();
        dockManager.position = () => {
            // nop it out
        };
    });

    describe("_animateIn", () => {
        it("inserts the first window", () => {
            expect(() => {
                var first = new FakeWindow('first');
                dockManager._animateIn(first);
            }).to.not.throw(/already in _window/);
        });

        // TODO: this is desired behavior, but will require some
        // special casing
        it.skip("handles hiding and restoring from main", () => {
            var main = new FakeWindow('/');
            var conv = new FakeWindow('conv');

            dockManager.adopt(main);
            dockManager.adopt(conv);

            main.show();
            conv.show();

            main.should.have.property('_index', 0);
            conv.should.have.property('_index', 1);

            // now hide 
            main.hide();
            conv.hide();

            main.should.have.property('_index', 0);
            conv.should.have.property('_index', 0);

            // and show:
            main.show();
            main.should.have.property('_index', 0);

            conv.show();
            conv.should.have.property('_index', 1);
            main.should.have.property('_index', 1);
        });

        it("handles hiding in reverse and restoring in same", () => {
            var main = new FakeWindow('/');
            var conv = new FakeWindow('conv');

            dockManager.adopt(main);
            dockManager.adopt(conv);

            main.show();
            conv.show();

            main.should.have.property('_index', 0);
            conv.should.have.property('_index', 1);

            // now hide in reverse order...
            conv.hide();
            main.hide();

            // and show:
            conv.show();
            conv.should.have.property('_index', 0);

            main.show();
            conv.should.have.property('_index', 1);
            main.should.have.property('_index', 0);
        });
    });
});
