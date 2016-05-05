package camidion.chordhelper.mididevice;

/**
 * MIDIデバイス入出力タイプ
 */
public enum MidiDeviceInOutType {
	MIDI_NONE("No MIDI input/output supported", "No I/O"),
	MIDI_OUT("MIDI output devices (MIDI synthesizer etc.)", "OUT"),
	MIDI_IN("MIDI input devices (MIDI keyboard etc.)", "IN"),
	MIDI_IN_OUT("MIDI input/output devices (MIDI sequencer etc.)", "I/O");
	private String description;
	private String shortName;
	private MidiDeviceInOutType(String description, String shortName) {
		this.description = description;
		this.shortName = shortName;
	}
	public String getDescription() { return description; }
	public String getShortName() { return shortName; }
}
