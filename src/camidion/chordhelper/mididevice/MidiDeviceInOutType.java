package camidion.chordhelper.mididevice;

import javax.sound.midi.MidiEvent;
import javax.sound.midi.Receiver;
import javax.sound.midi.Transmitter;

/**
 * MIDIデバイス入出力タイプ
 */
public enum MidiDeviceInOutType {
	/**
	 * {@link #MIDI_IN}も{@link #MIDI_OUT}もサポートしていないデバイスを表します。
	 */
	MIDI_NONE("No MIDI input/output supported", "No I/O"),
	/**
	 * {@link Receiver}から受けた{@link MidiEvent}を音源や画面に出力するデバイスを表します。
	 */
	MIDI_OUT("MIDI output devices (MIDI synthesizer etc.)", "OUT"),
	/**
	 * キーボードやシーケンサから入力した{@link MidiEvent}を
	 * {@link Transmitter}から{@link Receiver}へ転送するデバイスを表します。
	 */
	MIDI_IN("MIDI input devices (MIDI keyboard etc.)", "IN"),
	/**
	 * {@link #MIDI_IN}と{@link #MIDI_OUT}の両方をサポートしたデバイスを表します。
	 */
	MIDI_IN_OUT("MIDI input/output devices (MIDI sequencer etc.)", "I/O");

	private MidiDeviceInOutType(String description, String shortName) {
		this.description = description;
		this.shortName = shortName;
	}
	public static MidiDeviceInOutType getValueFor(TransmitterListModel txListModel, ReceiverListModel rxListModel) {
		// tx:IN rx:OUT
		return rxListModel == null ?
			(txListModel == null ? MIDI_NONE : MIDI_IN) :
			(txListModel == null ? MIDI_OUT  : MIDI_IN_OUT);
	}
	public String getDescription() { return description; }
	private String description;
	public String getShortName() { return shortName; }
	private String shortName;

}
