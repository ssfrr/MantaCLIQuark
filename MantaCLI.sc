/*
MantaCLI

This class is intended to use with the MantaOSC or MantaMIDI command-line
applications that come with the libmanta library. It allows you to register
callbacks for touch events and control the leds.
*/

MantaCLI {
	classvar <padMaps;
    // global callbacks (called regardless of selected page)
    var >onPadVelocity;
    var >onButtonVelocity;
    var >onPadValue;
    var >onSliderValue;
    var >onSliderAccum;
    var device;
    var pages;
	// we need to keep track of the touch state so we can freeze things with the pedal
	var pedalDown = false;
	var activePads;
	var sustainedPads;
    var activePageIdx;
    var lastSliderValue;
    var sliderAccum;
    var >debug = false;
    // the scale is tweaked here to make it easier for a full swipe of the
    // slider to generate about a 0-1 accumulated value. This is used both
    // within MantaCLI and MantaPage
    const <sliderScale = 1.17;
    // maximum value reported for a Pad Value/Velocity. We use these to scale
    // the values to 0-1 in the callbacks
    const maxPadValue = 208;
    const maxPadVelocity = 105;

	*initClass {
		// TODO: make pad mapping more convenient - probably add a `setPadMap` function
		// that makes it so all indices in the callbacks are mapped
		padMaps = (
			\wickihayden: #[
				0,  2,  4,  6,  8, 10, 12, 14,
				7,  9, 11, 13, 15, 17, 19, 21,
				12, 14, 16, 18, 20, 22, 24, 26,
				19, 21, 23, 25, 27, 29, 31, 33,
				24, 26, 28, 30, 32, 34, 36, 38,
				31, 33, 35, 37, 39, 41, 43, 45]);
	}

    *new {| protocol=\midi ...deviceArgs |
        var device;
        switch (protocol,
            \osc, { device = MantaOSCDevice(*deviceArgs) },
            \midi, { device = MantaMIDIDevice(*deviceArgs) },
            {"Protocol must be one of \\osc or \\midi".error; ^nil; });
        if(device.isNil, {
            "Couldn't initialize device".error; ^nil;
            })
		^super.new.init(device);
    }

    init { | dev |
        device = dev;
        pages = [];
        lastSliderValue = [nil, nil];
        sliderAccum = [0, 0];
		activePads = ();
		sustainedPads = ();
        device.onPadVelocity = { | idx, rawVelocity |
			pedalDown.if {
				sustainedPads[idx].notNil.if {
					// this is a sustained pad, so we just update the state
					sustainedPads[idx] = rawVelocity;
				} {
					// pedal is down but this isn't a sustained pad, so it behaves as normal
					this.triggerPadVelocity(idx, rawVelocity);
					activePads[idx] = (rawVelocity > 0).if(rawVelocity, nil);
				}
			} {
				this.triggerPadVelocity(idx, rawVelocity);
				activePads[idx] = (rawVelocity > 0).if(rawVelocity, nil);
			}
        };
        device.onPadValue = { | idx, rawValue |
			sustainedPads[idx].isNil.if {
				this.triggerPadValue(idx, rawValue);
			}
        };
        device.onButtonVelocity = { | idx, rawVelocity |
            this.triggerButtonVelocity(idx, rawVelocity);
        };
        device.onSliderValue = { | idx, rawValue |
            this.triggerSliderValue(idx, rawValue);
        };
    }

    newPage {
        var page = MantaPage.new(device);
        pages = pages.add(page);
        if(activePageIdx.isNil, { activePageIdx = 0 });
        ^page;
    }

    enableLedControl {
        | enabled=true |
        device.enableLedControl(enabled);
        if(debug, {"MantaCLI: LED Control %\n".postf(enabled)});
    }

    draw {
        if(activePageIdx.notNil, { pages[activePageIdx].draw; });
    }

	pedal_ {
		| enabled |
		pedalDown = enabled;

		enabled.if {
			sustainedPads = activePads;
			activePads = ();
		} {
			sustainedPads.keys.do {
				| idx |
				(sustainedPads[idx] > 0).if {
					activePads[idx] = sustainedPads[idx];
				} {
					// this pad was active when the pedal went down but was inactive when the pedal went up
					this.triggerPadVelocity(idx, 0);
				}
			};
			sustainedPads = ();
		}
	}

    triggerPadVelocity {| idx, rawVelocity |
        var row = idx.div(8);
        var column = idx.mod(8);
        var value = min(1.0, rawVelocity / maxPadVelocity);
        onPadVelocity.value(idx, value, row, column);
        if(activePageIdx.notNil, {
            pages[activePageIdx].onPadVelocity.value(idx, value, row, column);
            });
        if(debug, {"MantaCLI: Row %, Column %, Velocity %\n".postf(row, column, value)});
    }

    triggerPadValue {| idx, rawValue |
        var row = idx.div(8);
        var column = idx.mod(8);
        var value = min(1.0, rawValue / maxPadValue);
        onPadValue.value(idx, value, row, column);
        if(activePageIdx.notNil, {
            pages[activePageIdx].onPadValue.value(idx, value, row, column);
            });
        if(debug, {"MantaCLI: Pad % (%, %), Value %\n".postf(idx, row, column, value)});
    }

    triggerButtonVelocity {| idx, rawVelocity |
        var velocity = min(1.0, rawVelocity / maxPadVelocity);
        onButtonVelocity.value(idx, velocity);
        if(activePageIdx.notNil, { pages[activePageIdx].onButtonVelocity.value(idx, velocity); });
        if(activePageIdx.notNil && (idx == 0) && (velocity > 0), {
            // cycle through the pages
            activePageIdx = (activePageIdx + 1).mod(pages.size);
            this.draw;
        });
        if(debug, {"MantaCLI: Button %, Velocity %\n".postf(idx, velocity)});
    }

    triggerSliderValue {| idx, rawValue |
        var value = if(rawValue == 65535, nil, rawValue/4096);
        if(value.notNil && lastSliderValue[idx].notNil, {
            var diff = value - lastSliderValue[idx];
            sliderAccum[idx] = sliderAccum[idx] + (diff*sliderScale);
            onSliderAccum.value(idx, sliderAccum[idx]);
            if(debug, {"MantaCLI: Slider %, AccumValue %\n".postf(idx, sliderAccum[idx])});
        });
        onSliderValue.value(idx, value);
        if(activePageIdx.notNil, {
            pages[activePageIdx].onSliderValue.value(idx, value);
            pages[activePageIdx].updateSlider(idx, value);
        });
        lastSliderValue[idx] = value;
        if(debug, {"MantaCLI: Slider %, Value %\n".postf(idx, value)});
    }
}

// internal class used for protocol-specific behavior
MantaMIDIDevice {
    var midiSender;
    // device-level callbacks
    var >onPadVelocity;
    var >onButtonVelocity;
    var >onPadValue;
    var >onSliderValue;
    var currentLEDState; // 0: off, 1: amber, 2: red
    const sendChan = 0;
    const receiveChan = 0;

    *new { | receivePort=#["Manta Out", "Manta Out"],
             sendPort=#["Manta In", "Manta In"] |
        ^super.new.init(receivePort, sendPort);
    }

    init { | receivePort, sendPort |
        var port;
        midiSender = MIDIOut.newByName(sendPort[0], sendPort[1]);
        if(midiSender.isNil, {
            "Couldn't find output port \"%\":\"%\". Is MIDIClient Initialized?"
                .format(*sendPort)
                .error;
            ^nil;
        });

        port = MIDIIn.findPort(receivePort[0], receivePort[1]);
        if(port.isNil, {
            "Couldn't find input port \"%\":\"%\". Is MIDIClient Initialized?"
                .format(*receivePort)
                .error;
            ^nil;
        });
        // make these MIDIdefs if you create a new MantaCLI you don't end up
        // with leftover methods firing. The receivePort is in the names so you
        // can have multiple MantaCLIs with different Mantas
        MIDIdef.noteOn(("noteon" ++ receivePort).asSymbol,
            { | val, num, chan, src |
                case(
                    {num < 48},  { onPadVelocity.value(num, val); },
                    {(100 <= num) && (num < 104)},  { onButtonVelocity.value(num-100, val); },
                    {"MantaCLI: Got unexpected noteOn: note %, vel %".format(num, val).warn }
                    )
            }, nil, receiveChan, port.uid);

        MIDIdef.noteOff(("noteoff" ++ receivePort).asSymbol,
            { | val, num, chan, src |
                case(
                    {num < 48},  { onPadVelocity.value(num, val); },
                    {(100 <= num) && (num < 104)},  { onButtonVelocity.value(num-100, val); },
                    {"MantaCLI: Got unexpected noteOff: note %, vel %".format(num, val).warn }
                )
            }, nil, receiveChan, port.uid);

        MIDIdef.cc(("cc" ++ receivePort).asSymbol,
            { | val, num, chan, src |
                case(
                    {num < 48},  { onPadValue.value(num, val); },
                    {(100 <= num) && (num < 104)},  { /* ignore button value*/ },
                    {(104 <= num) && (num < 106)},  { this.triggerSliderValue(num-104, val); },
                    {"MantaCLI: Got unexpected MIDI CC: param %, vel  %".format(num, val).warn }
                )
            }, nil, receiveChan, port.uid);

        currentLEDState = 0!48;
    }

    enableLedControl {
        | enabled=true |
        "MantaCLI: enableLedControl has no effect when communicating with the Manta over MIDI".warn;
    }

    // takes an array for the amber LEDs and redLEDs. Each element of the array
    // is the number of active requests to turn on that LED
    draw { | amberLeds, redLeds |
        48.do { | idx |
            var desiredState = case(
                {redLeds[idx] > 0}, {2},
                {amberLeds[idx] > 0}, {1},
                {0}
                );
            if(desiredState != currentLEDState[idx], {
                // 0 turns LED off, 1-64 (or 65?) turns it amber, >65 turns it red
                midiSender.noteOn(sendChan, idx+1, desiredState*50);
                currentLEDState[idx] = desiredState;
            })
        }
    }

    triggerSliderValue { | idx, value |
        // values get squished down to 0-127 to fit through MIDI. :(
        if(value == 127, {
            // this signals when the user takes their finger off
            onSliderValue.value(idx, 65535)
        }, {
            onSliderValue.value(idx, value * 4096 / 127)
        })
    }
}

// internal class used for protocol-specific behavior
MantaOSCDevice {
    var oscSender;
    // device-level callbacks
    var >onPadVelocity;
    var >onButtonVelocity;
    var >onPadValue;
    var >onSliderValue;

    *new { | receivePort=31416, sendPort=31417 |
        ^super.new.init(receivePort, sendPort);
    }

    // we put most of the setup in this init method so we have access to the
    // internal properties
    init { | receivePort, sendPort |
        oscSender = NetAddr("localhost", sendPort);

        // we include the receive port in the OSCdef name to make sure that
        // there's only 1 OSCdef per receive port, but we can have multiple
        // mantas on different ports
        OSCdef(("padvalue-" ++ receivePort).asSymbol, {
            | msg |
            onPadValue.value(msg[1], msg[2])
        }, '/manta/continuous/pad', recvPort: receivePort);

        OSCdef(("slidervalue-" ++ receivePort).asSymbol, {
            | msg |
            onSliderValue.value(msg[1], msg[2]);
        }, '/manta/continuous/slider', recvPort: receivePort);

        OSCdef(("padvelocity-" ++ receivePort).asSymbol, {
            | msg |
            onPadVelocity.value(msg[1], msg[2]);
        }, '/manta/velocity/pad', recvPort: receivePort);

        OSCdef(("buttonvelocity-" ++ receivePort).asSymbol, {
            | msg |
            onButtonVelocity.value(msg[1], msg[2]);
        }, '/manta/velocity/button', recvPort: receivePort);
    }

    // takes an array for the amber LEDs and redLEDs. Each element of the array
    // is the number of active requests to turn on that LED
    draw { | amberLeds, redLeds |
        // each byte of the mask represents a row of LEDs. First is the 6 rows of amber, then the 6 rows of red
        var mask = Int8Array[0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0];
        [[amberLeds, 0], [redLeds, 6]].do {
            | args |
            var leds = args[0];
            var maskOffset = args[1];
            leds.do {
                | padcount, idx |
                if(padcount > 0, {
                    var row = idx.div(8);
                    var column = idx.mod(8);
                    mask[row+maskOffset] = mask[row+maskOffset] | (0x80 >> column);
                })
            };
        };
        oscSender.sendMsg('/manta/led/pad/frame', "all", mask);
    }

    enableLedControl {
        | enabled=true |
        oscSender.sendMsg('/manta/ledcontrol', "padandbutton", if(enabled, 1, 0));
    }
}

/*
Often Manta-based apps will want several pages to manipulate different aspects of the app's state. A MantaPage holds all the LED state for that page, as well as allows the user to register callbacks that only apply when that page is active.
*/

MantaPage {
    var device;
    var padLeds;
    var <>onPadVelocity; // callback for pad velocity (note on/off) events
    var <>onButtonVelocity; // callback for button velocity (note on/off) events
    var <>onPadValue; // callback for pad value events
    var <>onSliderValue; // callback for slider value events
    var <>onSliderAccum; // callback for accumulated slider events
    var lastSliderValue;
    var sliderAccum;

    *new {
        | device |
        ^super.new.init(device);
    }

    init { | dev |
        device = dev;
        padLeds = (amber: 0!48, red: 0!48);
        lastSliderValue = [nil, nil];
        sliderAccum = [0, 0];
    }

    setPadByIndex { | idx, color=\amber |
        padLeds[color][idx] = padLeds[color][idx] + 1;
    }

    setPadByRowCol { | row, col, color=\amber |
        this.setPadByIndex(row*8+col, color);
    }

    clearPadByIndex { | idx, color=\amber |
        if(padLeds[color][idx] > 0, {
            padLeds[color][idx] = padLeds[color][idx] - 1;
        }, {
            "% Pad % (row %, column %) cleared too many times\n"
                .format(color, idx, idx.div(8), idx.mod(8))
                .warn;
        });
    }

    clearPadByRowCol { | row, col, color=\amber |
        this.clearPadByIndex(row*8+col, color);
    }

    resetPadByIndex { | idx |
        padLeds[\amber][idx] = 0;
        padLeds[\red][idx] = 0;
    }

    resetPadByRowCol { | row, col |
        this.resetPadByIndex(row*8+col);
    }

    draw {
        device.draw(padLeds[\amber], padLeds[\red]);
    }

    updateSlider {
        | id, value |
        if(value.notNil && lastSliderValue[id].notNil, {
            var diff = value - lastSliderValue[id];
            sliderAccum[id] = sliderAccum[id] + (diff * MantaCLI.sliderScale);
            onSliderAccum.value(id, sliderAccum[id]);
        });
        lastSliderValue[id] = value;
    }
}
