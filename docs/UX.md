# How this mod's screens are supposed to behave

Rules earned the hard way, each one written down because it was broken first and someone had to
find out in game. If you are adding a screen or a setting, this is the bar.

## Text

**Every label goes through `fit()`.** Minecraft centres button text and lets it spill out of both
ends, so a long string does not get cut, it becomes an unreadable stub with the middle showing.
Spanish runs about a third longer than English, so a button that fits in English is not a button
that fits.

**Write the short version first.** "Ver a través" beats "Ver a través del terreno" truncated.
Trimming is the safety net, not the plan.

**Two texts on one line need a rule for who gives way.** The map has a mouse hint on the left and
a keyboard hint on the right; on a narrow screen they used to print on top of each other. Now the
secondary one disappears and the primary one trims.

## Buttons

**A button says what pressing it does.** Not the area it belongs to, not the noun. "Save" next to
"Export schematic" reads like two flavours of the same thing; "Save plan now" and
"Export .litematic" do not.

**If it cannot do anything, grey it out.** Layer up and down on "all layers" did nothing when
pressed, which is worse than being visibly unavailable.

**If it only makes sense somewhere, only show it there.** The Server tab does not exist when the
server does not have the mod.

**One job, one place.** Sharing lives in the Server tab. A half version of it elsewhere is how you
end up with two buttons that do different amounts of the same thing.

## Numbers

**Anything with a range is a `StepperWidget`.** That means: fills like a bar so the value's place
in its range is visible, ends marked `-` and `+`, left click up, **right click down**, and the
**scroll wheel** anywhere over it. People try all of those. Two tiny buttons with a label wedged
between them answer to exactly one of them.

**The label gives way to the value, never the other way round.**

## State

**Nothing is lost by closing a screen.** The plan saves itself. Say so on screen, next to the
manual save, or people will hunt for the button that keeps their work.

**A text box does something on its own.** The name box used to do nothing at all until you found
Save. Now it renames as you type.

**Settings survive a restart.** The layer filter lived only in memory, so every restart quietly
put you back on "all layers" halfway through a course.

**Changing a default does not reach anyone who already ran the mod.** Saved config and
`options.txt` both win over new defaults. A new default needs a migration: `BeaconatorConfig`
moves a value only if it is still exactly the old default, and `Keys` binds a key only if nobody
has bound it. Skipping this is how `Shift + B` stopped opening the screen for everyone who had
played before.

## Colour

**Three states you can name beat a gradient you have to interpret.** Untouched, half built,
done. The old white to green fade made a node missing one of its two beacons look finished.

**Colour is a setting, not a constant.** The map is greens, greys and blues; whatever you picked
looks wrong on someone else's terrain. Palette cycling with a swatch on the button, not hex
codes typed into a file.

**Two different things do not share a colour.** Excluded and dropped nodes did, so making one
visible lit up two hundred of the other.

## Keys

**Modifiers are part of a binding.** `G`, `Shift + G` and `Ctrl + Shift + G` are three bindings.
Matching is exact, so a plain binding does not also fire under its own modified version.

**Say when a key is already taken.** A modded profile has hundreds of bindings and a silent clash
just means two things happen at once and nobody knows why.

**The default binding has to be actually free.** Check `options.txt` and the mod configs of
whatever else is installed. `V` looked free and belongs to Tweakeroo's accurate placement, which
is exactly the key someone is holding while they build.

## Commands

**A command only exists when the screen cannot do the job.** A screen shows the current value; a
command cannot. Thirty subcommands mirroring the tabs were thirty things to keep in step, all of
them worse than the button next to them.
