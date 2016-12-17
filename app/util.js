
module.exports.urlForConvId = function urlForConvId(convId) {
    return `/c/${convId}`;
};

module.exports.parsePresencePacket = function parsePresencePacket(packet) {
    const content = packet[0][0];
    return {
        participant_id: {
            gaia_id: content[0][0],
            chat_id: content[0][1]
        },
        // these are total guesses based off the protobuf def here:
        // https://github.com/tdryer/hangups/blob/master/hangups/hangouts.proto#L85
        reachable: content[1][0] !== 0,
        available: content[1][1] !== 0,
    };
};
