
const chai = require('chai');
const {parsePresencePacket} = require('../../app/util');

// var expect = chai.expect;
chai.should();


describe("parsePresencePacket", () => {
    it("works", () => {
        const packet = [
            [
                [
                    [
                        "its",
                        "kaylee"
                    ],
                    [
                        1,
                        0
                    ]
                ]
            ]
        ];

        const parsed = parsePresencePacket(packet);
        parsed.should.deep.equal({
            participant_id: {
                gaia_id: "its",
                chat_id: "kaylee"
            },
            reachable: true,
            available: false,
        });
    });
});
