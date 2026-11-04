# Teacher review checklist (T4.5)

The content in `content/*.json` was written by an AI assistant from general knowledge, without access to the physical lab devices. Each device is **based on an assumed model** named in its overview. Please check each item against your actual lab equipment and local safety rules, correct anything wrong in `content/<id>.json` (or tell the project owner), and sign off at the end. Mark an item `[x]` when verified, or write the correction next to it.

## All devices
- [ ] Device list matches the lab (10 devices)
- [ ] Front-panel part names, positions and counts match the real model (3D layout is a stylized approximation)
- [ ] Safety rules and tips match your lab's rules
- [ ] Quiz answers and explanations are correct; wording suits the students' level

## 1. Digital Multimeter (`digital_multimeter`, generic handheld DMM)
- [ ] CAT ratings: safety rule says check CAT II, III or IV; quiz says a distribution panel is a CAT III location and needs CAT III or higher, and CAT I/II answers are wrong
- [ ] 10 A jack: "typically 10 A for a limited time", fused with a high-energy fuse; maximum measurement time marked on the meter
- [ ] Current-fuse faults (0.00 mA with working circuit; blown 10A fuse) and the "switch to the 10A jack" advice
- [ ] Ghost-voltage fault (0.3 V AC on a disconnected cable) and the low-impedance mode remedy
- [ ] Leads in current mode across a voltage source shorts the source

## 2. Analog Multimeter (`analog_multimeter`, based on Sanwa YX-360TRF)
- [ ] Panel layout: range selector, 0 ohm adjuster, mechanical zero, jacks, battery/fuse cover
- [ ] Battery and fuse access via rear cover; fuse "same rating and type" (confirm the actual rating)
- [ ] 0 ohm adjust must be repeated after each resistance range change
- [ ] Rule to start on the highest range; parallax mirror; dB scale mention
- [ ] "Voltage works but all ohm ranges dead: check battery and fuse"

## 3. Digital Clamp Meter (`digital_clamp_meter`, based on UNI-T UT203)
- [ ] CAT rating and voltage rating of the real unit (content says to respect the CAT rating but gives no number)
- [ ] Maximum current and jaw capacity; whether the model measures AC only or AC and DC (fault scenario shows a DC zero offset of 0.8 A)
- [ ] Voltage, resistance and continuity on test-lead jacks; 10A jack quiz option and input fuse fault
- [ ] Clamping a two-core cable gives about 0 A (single conductor rule)
- [ ] Zero/offset procedure on the DC range

## 4. Digital Oscilloscope (`digital_oscilloscope`, based on Rigol DS1054Z)
- [ ] Front-panel layout: 4 channels, vertical/horizontal/trigger sections, RUN/STOP, AUTO, menu keys
- [ ] Probe compensation output: "about 1 kHz, 3 V peak to peak" (verify on the real unit)
- [ ] Probe ratio setting is manual (quiz says the scope does not detect it automatically on every model)
- [ ] Maximum input voltage for the probe and BNC input, and that the ground clip is earth-referenced
- [ ] Procedure "connect and compensate a 10X probe" menu path (CH1 > probe ratio)
- [ ] Quiz arithmetic: 3 div x 2 V/div = 6 Vpp; 4 ms period = 250 Hz

## 5. Function Generator (`function_generator`, based on Rigol DG1022Z)
- [ ] Front-panel layout: screen, softkeys, keypad, knob, CH1/CH2 output buttons, BNC outputs
- [ ] 50 ohm output impedance; "load 50 ohm vs High Z doubles the displayed voltage" explanation
- [ ] Frequency and amplitude limits (none stated; add if the lab has a policy)
- [ ] 3.3 V logic procedure: 3.3 Vpp with 1.65 V offset gives 0 to 3.3 V; check that this is safe for the lab's devices
- [ ] Unit entry advice (kHz vs MHz); quiz: peak is half of the peak-to-peak value

## 6. DC Power Supply (`dc_power_supply`, based on GW Instek GPS-3030D)
- [ ] Maximum voltage and current of the real unit (30 V / 3 A class): content states no limits; add if wanted
- [ ] Layout: two displays, coarse/fine knobs for V and I, output switch, CC/CV lights, terminals
- [ ] CV/CC explanation and the current-limit procedure (set limit before connecting the load)
- [ ] Quiz arithmetic: 12 V x 0.5 A = 6 W; current limit of "a little above 0.5 A"
- [ ] Fuse and mains checks "by an authorised person" matches lab policy

## 7. Soldering Station (`soldering_station`, based on Hakko FX-888D)
- [ ] Soldering temperature: "typically around 320 to 380 degrees Celsius for general electronics, as your teacher specifies" (leaded vs lead-free; confirm your lab's setting)
- [ ] Station display and button layout (set/up/down, preset temperatures, calibration)
- [ ] Tip cleaning (sponge or brass wool), tinning, hand position, stand use
- [ ] Fault: display lit but tip never heats, error/sensor message
- [ ] Fume extraction, eye protection and burn-first-aid rules match lab policy

## 8. LCR Meter (`lcr_meter`, based on DER EE DE-5000)
- [ ] Test frequencies "100 Hz, 120 Hz, 1 kHz, 10 kHz, 100 kHz, depending on the model" (DE-5000 supports 100 Hz to 100 kHz, verify)
- [ ] Button names (FREQ, SER/PAR, etc.) and the auto-mode behaviour
- [ ] Series vs parallel model rule of thumb (series for low impedance, parallel for high impedance)
- [ ] Capacitor discharge before measuring; maximum input protection (meter input not rated for external voltage)
- [ ] Inductor frequency fault (30 percent lower at 100 kHz than at 1 kHz)

## 9. Insulation Tester (`insulation_tester`, based on Kyoritsu 3005A)
- [ ] Test voltages 250 / 500 / 1000 V DC: confirm the ranges the real model offers
- [ ] Quiz: 500 V DC for 230 V mains cabling and "circuits up to 500 V" (check against the standard your institution follows)
- [ ] Acceptable insulation resistance (fault scenario: motor winding 0.3 megohm vs over 100 megohm dry motor; no pass threshold is given)
- [ ] Procedure: circuit dead, isolated, tested for dead, capacitive circuits discharged after the test
- [ ] Do students use this device hands-on, or demonstration only (adjust the safety wording accordingly)
- [ ] Continuity function and live-circuit warning behaviour

## 10. Breadboard / Digital Trainer (`breadboard_trainer`, based on Global Specialties PB-505A; "generic trainer")
- [ ] Layout matches your trainer (switch bank, LED bank, seven-segment display, clock selector 1 Hz / 1 kHz / 10 kHz, 5 V posts, mains fuse)
- [ ] +5 V regulated supply for TTL; any extra rails (+/-12 V) on the lab trainer
- [ ] Logic switches give +5 V (logic 1) or ground (logic 0)
- [ ] Clock rates quoted (1 Hz, 1 kHz, 10 kHz) and quiz "about 1 Hz" for watching a counter
- [ ] TTL vs CMOS statements, if the lab uses CMOS parts

## Sign-off
- Reviewer name: ______________________  Role/department: ______________________
- Devices reviewed (list or "all"): ______________________
- Corrections needed (attach list): [ ] none  [ ] yes, see attached
- Signature: ______________________  Date: ______________
