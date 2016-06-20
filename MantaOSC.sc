/*
MantaOSC

This class is intended to use with the MantaOSC command-line application that comes as an example with the libmanta library. It allows you to register callbacks for touch events and control the leds.
*/

MantaOSC {
    var oscSender;
    var >padVelocity; // callback for pad velocity (note on/off) events (called for all pages)
    var >buttonVelocity; // callback for button velocity (note on/off) events (called for all pages)
    var >padValue; // callback for pad value events (called for all pages)
    var >sliderValue; // callback for slider value events (called for all pages)
    var pages;
    var activePageIdx;

    *new {
        | receivePort=31416, sendPort=31417 |
        var instance = super.new;
        instance.init(receivePort, sendPort);
        ^instance;
    }

    // we put most of the setup in this init method so we have access to the internal properties
    init {
        | receivePort, sendPort |

        oscSender = NetAddr("localhost", sendPort);
        pages = [];

        // we include the receive port in the OSCdef name to make sure that there's only 1 OSCdef per receive port, but we can have multiple mantas on different ports
        OSCdef(("padvalue-" ++ receivePort).asSymbol, {
            | msg |
            var padnum = msg[1];
            var value = msg[2];
            padValue.value(padnum, value);
            if(activePageIdx.notNil, { pages[activePageIdx].padValue.value(padnum, value); });
        }, '/manta/continuous/pad', recvPort: receivePort);

        OSCdef(("slidervalue-" ++ receivePort).asSymbol, {
            | msg |
            var id = msg[1];
            var value = if(msg[2] == 65535, nil, msg[2]);
            sliderValue.value(id, value);
            if(activePageIdx.notNil, { pages[activePageIdx].sliderValue.value(id, value); });

        }, '/manta/continuous/slider', recvPort: receivePort);

        OSCdef(("padvelocity-" ++ receivePort).asSymbol, {
            | msg |
            var padnum = msg[1];
            var value = msg[2];
            padVelocity.value(padnum, value);
            if(activePageIdx.notNil, { pages[activePageIdx].padVelocity.value(padnum, value); });
        }, '/manta/velocity/pad', recvPort: receivePort);

        OSCdef(("buttonvelocity-" ++ receivePort).asSymbol, {
            | msg |
            var buttonnum = msg[1];
            var velocity = msg[2];
            buttonVelocity.value(buttonnum, velocity);
            if(activePageIdx.notNil, { pages[activePageIdx].buttonVelocity.value(buttonnum, velocity); });
            // button 2 cycles through pages
            if(activePageIdx.notNil && (buttonnum == 0) && (velocity > 0), {
                activePageIdx = (activePageIdx + 1).mod(pages.size);
                this.draw;
            });
        }, '/manta/velocity/button', recvPort: receivePort);
    }

    enableLedControl {
        | enabled=true |
        oscSender.sendMsg('/manta/ledcontrol', "padandbutton", if(enabled, 1, 0));
    }

    draw {
        if(activePageIdx.notNil, { pages[activePageIdx].draw; });
    }

    newPage {
        var page = MantaPage.new(oscSender);
        pages = pages.add(page);
        if(activePageIdx.isNil, { activePageIdx = 0 });
        ^page;
    }
}

/*
Often Manta-based apps will want several pages to manipulate different aspects of the app's state. A MantaPage holds all the LED state for that page, as well as allows the user to register callbacks that only apply when that page is active.
*/

MantaPage {
    var padLeds;
    var oscSender;
    var <>padVelocity; // callback for pad velocity (note on/off) events
    var <>buttonVelocity; // callback for button velocity (note on/off) events
    var <>padValue; // callback for pad value events
    var <>sliderValue; // callback for slider value events

    *new {
        | oscSender |
        var padLeds = (amber: 0!48, red: 0!48);
        ^super.newCopyArgs(padLeds, oscSender);
    }

    setPad {
        | row, col, color=\amber |
        var padNum = row * 8 + col;
        padLeds[color][padNum] = padLeds[color][padNum] + 1;
    }

    clearPad {
        | row, col, color=\amber |
        var padNum = row * 8 + col;
        if(padLeds[color][padNum] > 0, {
            padLeds[color][padNum] = padLeds[color][padNum] - 1;
        }, {
            "WARNING: % Pad % (row %, column %) cleared too may times\n".postf(color, padNum, padNum.div(8), padNum.mod(8));
        });
    }

    draw {
        // each byte of the mask represents a row of LEDs. First is the 6 rows of amber, then the 6 rows of red
        var mask = Int8Array[0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0];
        [[\amber, 0], [\red, 6]].do {
            | args |
            var color = args[0];
            var maskOffset = args[1];
            padLeds[color].do {
                | padcount, idx |
                if(padcount > 0, {
                    var row = idx.div(8);
                    var column = idx.mod(8);
                    mask[row+maskOffset] = mask[row] | (0x80 >> column);
                })
            };
        };
        oscSender.sendMsg('/manta/led/pad/frame', "all", mask);
    }
}