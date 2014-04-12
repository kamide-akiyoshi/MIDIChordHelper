package camidion.chordhelper.mididevice;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MidiChannel;
import javax.sound.midi.MidiMessage;
import javax.sound.midi.ShortMessage;

import camidion.chordhelper.music.MIDISpec;

/**
 * 仮想MIDIデバイスからのMIDIチャンネルメッセージ送信クラス
 */
public class MidiChannelMessageSender implements MidiChannel {
	/**
	 * このMIDIチャンネルの親となる仮想MIDIデバイス
	 */
	private VirtualMidiDevice vmd;
	/**
	 * MIDIチャンネルインデックス（チャンネル 1 のとき 0）
	 */
	private int channel;
	/**
	 * 指定の仮想MIDIデバイスの指定のMIDIチャンネルの
	 * メッセージを送信するためのインスタンスを構築します。
	 * @param vmd 仮想MIDIデバイス
	 * @param channel MIDIチャンネルインデックス（チャンネル 1 のとき 0）
	 */
	public MidiChannelMessageSender(VirtualMidiDevice vmd, int channel) {
		this.vmd = vmd;
		this.channel = channel;
	}
	/**
	 * 仮想MIDIデバイスからこのMIDIチャンネルのショートメッセージを送信します。
	 * @param command このメッセージで表される MIDI コマンド
	 * @param data1 第 1 データバイト
	 * @param data2 第 2 データバイト
	 * @see ShortMessage#setMessage(int, int, int, int)
	 */
	public void sendShortMessage(int command, int data1, int data2) {
		ShortMessage short_msg = new ShortMessage();
		try {
			short_msg.setMessage( command, channel, data1, data2 );
		} catch(InvalidMidiDataException e) {
			e.printStackTrace();
			return;
		}
		vmd.sendMidiMessage((MidiMessage)short_msg);
	}
	public void noteOff( int note_no ) { noteOff( note_no, 64 ); }
	public void noteOff( int note_no, int velocity ) {
		sendShortMessage( ShortMessage.NOTE_OFF, note_no, velocity );
	}
	public void noteOn( int note_no, int velocity ) {
		sendShortMessage( ShortMessage.NOTE_ON, note_no, velocity );
	}
	public void setPolyPressure(int note_no, int pressure) {
		sendShortMessage( ShortMessage.POLY_PRESSURE, note_no, pressure );
	}
	public int getPolyPressure(int noteNumber) { return 0x40; }
	public void controlChange(int controller, int value) {
		sendShortMessage( ShortMessage.CONTROL_CHANGE, controller, value );
	}
	public int getController(int controller) { return 0x40; }
	public void programChange( int program ) {
		sendShortMessage( ShortMessage.PROGRAM_CHANGE, program, 0 );
	}
	public void programChange(int bank, int program) {
		controlChange( 0x00, ((bank>>7) & 0x7F) );
		controlChange( 0x20, (bank & 0x7F) );
		programChange( program );
	}
	public int getProgram() { return 0; }
	public void setChannelPressure(int pressure) {
		sendShortMessage( ShortMessage.CHANNEL_PRESSURE, pressure, 0 );
	}
	public int getChannelPressure() { return 0x40; }
	public void setPitchBend(int bend) {
		// NOTE: Pitch Bend data byte order is Little Endian
		sendShortMessage(
			ShortMessage.PITCH_BEND,
			(bend & 0x7F), ((bend>>7) & 0x7F)
		);
	}
	public int getPitchBend() { return MIDISpec.PITCH_BEND_NONE; }
	public void allSoundOff() { controlChange( 0x78, 0 ); }
	public void resetAllControllers() { controlChange( 0x79, 0 ); }
	public boolean localControl(boolean on) {
		controlChange( 0x7A, on ? 0x7F : 0x00 );
		return false;
	}
	public void allNotesOff() { controlChange( 0x7B, 0 ); }
	public void setOmni(boolean on) {
		controlChange( on ? 0x7D : 0x7C, 0 );
	}
	public boolean getOmni() { return false; }
	public void setMono(boolean on) {}
	public boolean getMono() { return false; }
	public void setMute(boolean mute) {}
	public boolean getMute() { return false; }
	public void setSolo(boolean soloState) {}
	public boolean getSolo() { return false; }
}