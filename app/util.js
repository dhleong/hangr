
module.exports.isWindows = require('os').platform() === 'win32';

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

module.exports.throttle = function throttle(fn, periodMs) {
    var state = {
        periodStart: Date.now(),
        token: null,
    };

    function throttled(...args) {
        if (state.token) clearTimeout(state.token);

        var now = Date.now();
        var leftInPeriod = periodMs - (now - state.periodStart);
        if (leftInPeriod <= 0) {
            // past period (or first call); call through
            fn(...args);

            state.periodStart = now;
            state.token = null;
        } else {
            // in the period; set a timeout and re-execute at the end
            state.token = setTimeout(
                throttled.bind(throttled, ...args),
                leftInPeriod);
        }
    }

    throttled.clear = function() {
        clearTimeout(state.token);
        state.token = null;
    };

    return throttled;
};
