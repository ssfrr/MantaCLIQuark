/*
MantaOSC

This class is intended to use with the MantaOSC command-line application that comes as an example with the libmanta library. It allows you to register callbacks for touch events and control the leds.
*/

MantaOSC {
    var oscSender;
    var >onPadVelocity; // callback for pad velocity (note on/off) events (called for all pages)
    var >onButtonVelocity; // callback for button velocity (note on/off) events (called for all pages)
    var >onPadValue; // callback for pad value events (called for all pages)
    var >onSliderValue; // callback for slider value events (called for all pages)
    var >onSliderAccum; // callback for accumulated slider value (called for all pages)
    var pages;
    var activePageIdx;
    var lastSliderValue;
    var sliderAccum;
    // the scale is tweaked here to make it easier for a full swipe of the slider
    // to generate about a 0-1 accumulated value. This is used both within MantaOSC and MantaPage
    const <sliderScale = 1.17;

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
        lastSliderValue = [nil, nil];
        sliderAccum = [0, 0];

        // we include the receive port in the OSCdef name to make sure that there's only 1 OSCdef per receive port, but we can have multiple mantas on different ports
        OSCdef(("padvalue-" ++ receivePort).asSymbol, {
            | msg |
            var padnum = msg[1];
            var row = padnum.div(8);
            var column = padnum.mod(8);
            var value = msg[2];
            onPadValue.value(row, column, value);
            if(activePageIdx.notNil, { pages[activePageIdx].onPadValue.value(row, column, value); });
        }, '/manta/continuous/pad', recvPort: receivePort);

        OSCdef(("slidervalue-" ++ receivePort).asSymbol, {
            | msg |
            var id = msg[1];
            var value = if(msg[2] == 65535, nil, msg[2]);
            if(value.notNil && lastSliderValue[id].notNil, {
                var diff = value - lastSliderValue[id];
                sliderAccum[id] = sliderAccum[id] + diff.linlin(-4096, 4096, -1*sliderScale, sliderScale);
                onSliderAccum.value(id, sliderAccum[id]);
            });
            onSliderValue.value(id, value);
            if(activePageIdx.notNil, {
                pages[activePageIdx].onSliderValue.value(id, value);
                pages[activePageIdx].updateSlider(id, value);
            });
            lastSliderValue[id] = value;

        }, '/manta/continuous/slider', recvPort: receivePort);

        OSCdef(("padvelocity-" ++ receivePort).asSymbol, {
            | msg |
            var padnum = msg[1];
            var row = padnum.div(8);
            var column = padnum.mod(8);
            var value = msg[2];
            onPadVelocity.value(row, column, value);
            if(activePageIdx.notNil, { pages[activePageIdx].onPadVelocity.value(row, column, value); });
        }, '/manta/velocity/pad', recvPort: receivePort);

        OSCdef(("buttonvelocity-" ++ receivePort).asSymbol, {
            | msg |
            var buttonnum = msg[1];
            var velocity = msg[2];
            onButtonVelocity.value(buttonnum, velocity);
            if(activePageIdx.notNil, { pages[activePageIdx].onButtonVelocity.value(buttonnum, velocity); });
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
    var oscSender;
    var padLeds;
    var <>onPadVelocity; // callback for pad velocity (note on/off) events
    var <>onButtonVelocity; // callback for button velocity (note on/off) events
    var <>onPadValue; // callback for pad value events
    var <>onSliderValue; // callback for slider value events
    var <>onSliderAccum; // callback for accumulated slider events
    var lastSliderValue;
    var sliderAccum;


    *new {
        | oscSender |
        var instance = super.newCopyArgs(oscSender);
        instance.init;
        ^instance;
    }

    init {
        padLeds = (amber: 0!48, red: 0!48);
        lastSliderValue = [nil, nil];
        sliderAccum = [0, 0];
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

    updateSlider {
        | id, value |
        if(value.notNil && lastSliderValue[id].notNil, {
            var diff = value - lastSliderValue[id];
            sliderAccum[id] = sliderAccum[id] + diff.linlin(-4096, 4096, -1*MantaOSC.sliderScale, MantaOSC.sliderScale);
            onSliderAccum.value(id, sliderAccum[id]);
        });
        lastSliderValue[id] = value;
    }
}