package camidion.chordhelper.mididevice;

import java.util.List;
import java.util.Vector;

import javax.sound.midi.MidiChannel;
import javax.sound.midi.MidiMessage;
import javax.sound.midi.MidiUnavailableException;
import javax.sound.midi.Receiver;
import javax.sound.midi.Transmitter;

import camidion.chordhelper.music.MIDISpec;

/**
 * 仮想MIDIデバイスの最小限の実装を提供するクラス
 */
public abstract class AbstractVirtualMidiDevice implements VirtualMidiDevice {
	/**
	 * この仮想デバイスのMIDIチャンネルの配列（MIDIメッセージ送信用）
	 */
	protected MidiChannelMessageSender[]
		channels = new MidiChannelMessageSender[MIDISpec.MAX_CHANNELS];
	/**
	 * 仮想MIDIデバイスを構築します。
	 */
	protected AbstractVirtualMidiDevice() {
		for( int i=0; i<channels.length; i++ )
			channels[i] = new MidiChannelMessageSender(this,i);
	}
	@Override
	public MidiChannel[] getChannels() { return channels; }
	/**
	 * MIDIデバイスを開いているときtrue
	 */
	protected boolean isOpen = false;
	/**
	 * 先頭のマイクロ秒位置（-1 で不定）
	 */
	protected long microsecondOrigin = -1;
	@Override
	public boolean isOpen() { return isOpen; }
	@Override
	public long getMicrosecondPosition() {
		return (microsecondOrigin == -1 ? -1: System.nanoTime()/1000 - microsecondOrigin);
	}
	@Override
	public void open() {
		isOpen = true;
		microsecondOrigin = System.nanoTime()/1000;
	}
	@Override
	public void close() {
		txList.clear();
		isOpen = false;
	}
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
		if( maxReceivers == 0 )
			return;
		if( ! rxList.isEmpty() )
			rxList.clear();
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
			public void close() { txList.remove(this); }
			public Receiver getReceiver() { return rx; }
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
}