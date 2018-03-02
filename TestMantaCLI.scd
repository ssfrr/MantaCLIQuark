Quarks.install("/Users/srussell/Dropbox/projects/active/MantaCLIQuark")
Quarks.install("C:\\Users\\sfr\\Dropbox\\projects\\active\\MantaCLIQuark")


(
var manta = MantaCLI();
var pages = [];
3.do { pages = pages.add(manta.newPage)};

manta.onPadVelocity = { | pad, vel | "Global - Pad: %, Velocity: %\n".postf(pad, vel)};
manta.onPadValue = { | pad, val | "Global - Pad: %, Value: %\n".postf(pad, val)};
manta.onButtonVelocity = { | btn, vel | "Global - Button: %, Velocity: %\n".postf(btn, vel)};
manta.onSliderValue = { | slider, val | "Global - Slider: %, Value: %\n".postf(slider, val)};
manta.onSliderAccum = { | slider, val | "Global - Slider Accum: %, Value: %\n".postf(slider, val)};

pages.do {
    | page, idx |
    page.onPadVelocity = { | pad, vel | "Page % - Pad: %, Velocity: %\n".postf(idx, pad, vel)};
    page.onPadValue = { | pad, val | "Page % - Pad: %, Value: %\n".postf(idx, pad, val)};
    page.onButtonVelocity = { | btn, vel | "Page % - Button: %, Velocity: %\n".postf(idx, btn, vel)};
    page.onSliderValue = { | slider, val | "Page % - Slider: %, Value: %\n".postf(idx, slider, val)};
    page.onSliderAccum = { | slider, val | "Page % - Slider Accum: %, Value: %\n".postf(idx, slider, val)};
};

manta.enableLedControl();
)

m = MantaCLI.new
{ {m.draw; (1/30).sleep;}.loop; }.fork

x = m.newPage
y = m.newPage

m.enableLedControl
x.clearPad(3, 2);
x.setPad(3, 2);
m.draw

Quarks.folder
