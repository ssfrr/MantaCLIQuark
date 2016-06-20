Quarks.install("/Users/srussell/Dropbox/projects/active/MantaOSCQuark")
Quarks.gui
(
var manta = MantaOSC.new;
var pages = [];
3.do { pages = pages.add(manta.newPage)};

manta.padVelocity = { | pad, vel | "Global - Pad: %, Velocity: %\n".postf(pad, vel)};
manta.padValue = { | pad, val | "Global - Pad: %, Value: %\n".postf(pad, val)};
manta.sliderValue = { | slider, val | "Global - Slider: %, Value: %\n".postf(slider, val)};

pages.do {
    | page, idx |
    page.padVelocity = { | pad, vel | "Page % - Pad: %, Velocity: %\n".postf(idx, pad, vel)};
    page.padValue = { | pad, val | "Page % - Pad: %, Value: %\n".postf(idx, pad, val)};
    page.sliderValue = { | slider, val | "Page % - Slider: %, Value: %\n".postf(idx, slider, val)};
};

manta.enableLedControl();
)

m = MantaOSC.new
{ {m.draw; (1/30).sleep;}.loop; }.fork

x = m.newPage
y = m.newPage

m.enableLedControl
x.clearPad(3, 2);
x.setPad(3, 2);
m.draw

Quarks.folder