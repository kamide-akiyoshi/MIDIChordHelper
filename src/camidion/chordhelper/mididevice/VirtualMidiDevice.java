package camidion.chordhelper.mididevice;

import javax.sound.midi.MidiChannel;
import javax.sound.midi.MidiDevice;
import javax.sound.midi.MidiMessage;

/**
 * 仮想MIDIデバイス
 */
public interface VirtualMidiDevice extends MidiDevice {
	/**
	 * この仮想MIDIデバイスのMIDIチャンネルを返します。
	 * @return MIDIチャンネル
	 */
	MidiChannel[] getChannels();
	/**
	 * MIDIメッセージを送信します。
	 * @param msg MIDIメッセージ
	 */
	void sendMidiMessage(MidiMessage msg);
}
