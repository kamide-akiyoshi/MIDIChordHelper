package camidion.chordhelper.mididevice;

/**
 * MIDIデバイス入出力タイプ
 */
public enum MidiDeviceInOutType {
	MIDI_OUT("MIDI output devices (MIDI synthesizer etc.)"),
	MIDI_IN("MIDI input devices (MIDI keyboard etc.)"),
	MIDI_IN_OUT("MIDI input/output devices (MIDI sequencer etc.)");
	private String description;
	private MidiDeviceInOutType(String description) {
		this.description = description;
	}
	public String getDescription() {
		return description;
	}
}