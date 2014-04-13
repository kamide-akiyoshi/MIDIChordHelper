package camidion.chordhelper.mididevice;

import java.util.List;
import java.util.Vector;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MidiChannel;
import javax.sound.midi.MidiMessage;
import javax.sound.midi.MidiUnavailableException;
import javax.sound.midi.Receiver;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.Transmitter;

import camidion.chordhelper.music.MIDISpec;

/**
 * 仮想MIDIデバイスの最小限の実装を提供するクラス
 */
public abstract class AbstractVirtualMidiDevice implements VirtualMidiDevice {
	/**
	 * 仮想MIDIデバイスを構築します。
	 */
	protected AbstractVirtualMidiDevice() {
		channels = new VirtualDeviceMidiChannel[MIDISpec.MAX_CHANNELS];
		for( int i=0; i<channels.length; i++ )
			channels[i] = new VirtualDeviceMidiChannel(i);
	}
	protected MidiChannel[] channels;
	@Override
	public MidiChannel[] getChannels() { return channels; }
	@Override
	public long getMicrosecondPosition() {
		return (microsecondOrigin == -1 ? -1: System.nanoTime()/1000 - microsecondOrigin);
	}
	/**
	 * 先頭のマイクロ秒位置（-1 で不定）
	 */
	protected long microsecondOrigin = -1;
	@Override
	public boolean isOpen() { return isOpen; }
	protected boolean isOpen = false;
	@Override
	public void open() {
		isOpen = true;
		microsecondOrigin = System.nanoTime()/1000;
	}
	@Override
	public void close() { txList.clear(); isOpen = false; }
	/**
	 * レシーバのリスト
	 */
	protected List<Receiver> rxList = new Vector<Receiver>();
	@Override
	public List<Receiver> getReceivers() { return rxList; }
	private int maxReceivers = 1;
	/**
	 * この MIDI デバイスで MIDI データを受信するのに使用可能な
	 *  MIDI IN 接続の最大数を設定します。デフォルト値は -1 です。
	 * @param maxReceivers MIDI IN 接続の最大数、または利用可能な接続数に制限がない場合は -1。
	 */
	protected void setMaxReceivers(int maxReceivers) {
		this.maxReceivers = maxReceivers;
	}
	@Override
	public int getMaxReceivers() { return maxReceivers; }
	@Override
	public Receiver getReceiver() {
		return rxList.isEmpty() ? null : rxList.get(0);
	}
	protected void setReceiver(Receiver rx) {
		if( maxReceivers == 0 ) return;
		if( ! rxList.isEmpty() ) rxList.clear();
		rxList.add(rx);
	}
	/**
	 * トランスミッタのリスト
	 */
	protected List<Transmitter> txList = new Vector<Transmitter>();
	@Override
	public List<Transmitter> getTransmitters() { return txList; }
	private int maxTransmitters = -1;
	@Override
	public int getMaxTransmitters() { return maxTransmitters; }
	/**
	 * この MIDI デバイスで MIDI データを転送するのに使用可能な
	 *  MIDI OUT 接続の最大数を設定します。デフォルト値は -1 です。
	 * @param maxTransmitters MIDI OUT 接続の最大数、または利用可能な接続数に制限がない場合は -1。
	 */
	protected void setMaxTransmitters(int maxTransmitters) {
		this.maxTransmitters = maxTransmitters;
	}
	@Override
	public Transmitter getTransmitter() throws MidiUnavailableException {
		if( maxTransmitters == 0 ) {
			throw new MidiUnavailableException();
		}
		Transmitter new_tx = new Transmitter() {
			private Receiver rx = null;
			@Override
			public void close() { txList.remove(this); }
			@Override
			public Receiver getReceiver() { return rx; }
			@Override
			public void setReceiver(Receiver rx) { this.rx = rx; }
		};
		txList.add(new_tx);
		return new_tx;
	}
	@Override
	public void sendMidiMessage(MidiMessage msg) {
		long timestamp = getMicrosecondPosition();
		for( Transmitter tx : txList ) {
			Receiver rx = tx.getReceiver();
			if(rx != null) rx.send(msg, timestamp);
		}
	}
	/**
	 * チャンネルの実装
	 */
	private class VirtualDeviceMidiChannel implements MidiChannel {
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
		public VirtualDeviceMidiChannel(int channel) {
			this.channel = channel;
		}
		private void sendShortMessage(int command, int data1, int data2) {
			try {
				sendMidiMessage(new ShortMessage(command, channel, data1, data2));
			} catch (InvalidMidiDataException e) {
				e.printStackTrace();
			}
		}
		@Override
		public void noteOff(int noteNumber) {
			noteOff(noteNumber, 64);
		}
		@Override
		public void noteOff(int noteNumber, int velocity) {
			sendShortMessage(ShortMessage.NOTE_OFF, noteNumber, velocity);
		}
		@Override
		public void noteOn(int noteNumber, int velocity) {
			sendShortMessage(ShortMessage.NOTE_ON, noteNumber, velocity);
		}
		@Override
		public void setPolyPressure(int noteNumber, int pressure) {
			sendShortMessage(ShortMessage.POLY_PRESSURE, noteNumber, pressure);
		}
		@Override
		public int getPolyPressure(int noteNumber) { return 0x40; }
		@Override
		public void controlChange(int controller, int value) {
			sendShortMessage(ShortMessage.CONTROL_CHANGE, controller, value);
		}
		@Override
		public int getController(int controller) { return 0x40; }
		@Override
		public void programChange(int program) {
			sendShortMessage(ShortMessage.PROGRAM_CHANGE, program, 0);
		}
		@Override
		public void programChange(int bank, int program) {
			controlChange(0x00, ((bank>>7) & 0x7F));
			controlChange(0x20, (bank & 0x7F));
			programChange(program);
		}
		@Override
		public int getProgram() { return 0; }
		@Override
		public void setChannelPressure(int pressure) {
			sendShortMessage(ShortMessage.CHANNEL_PRESSURE, pressure, 0);
		}
		@Override
		public int getChannelPressure() { return 0x40; }
		@Override
		public void setPitchBend(int bend) {
			// NOTE: Pitch Bend data byte order is Little Endian
			sendShortMessage(ShortMessage.PITCH_BEND, (bend & 0x7F), ((bend>>7) & 0x7F));
		}
		@Override
		public int getPitchBend() { return MIDISpec.PITCH_BEND_NONE; }
		@Override
		public void allSoundOff() { controlChange(0x78, 0); }
		@Override
		public void resetAllControllers() { controlChange(0x79, 0); }
		@Override
		public boolean localControl(boolean on) {
			controlChange(0x7A, on?0x7F:0x00);
			return false;
		}
		@Override
		public void allNotesOff() { controlChange( 0x7B, 0 ); }
		@Override
		public void setOmni(boolean on) { controlChange(on?0x7D:0x7C, 0);
		}
		@Override
		public boolean getOmni() { return false; }
		@Override
		public void setMono(boolean on) {}
		@Override
		public boolean getMono() { return false; }
		@Override
		public void setMute(boolean mute) {}
		@Override
		public boolean getMute() { return false; }
		@Override
		public void setSolo(boolean soloState) {}
		@Override
		public boolean getSolo() { return false; }
	}
}