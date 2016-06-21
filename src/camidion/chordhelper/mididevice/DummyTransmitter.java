package camidion.chordhelper.mididevice;

import javax.sound.midi.Receiver;
import javax.sound.midi.Transmitter;

/**
 * レシーバを保持するだけのダミートランスミッタを表すクラスです。
 */
public class DummyTransmitter implements Transmitter {
	private Receiver receiver;
	@Override
	public void setReceiver(Receiver receiver) { this.receiver = receiver; }
	@Override
	public Receiver getReceiver() { return receiver; }
	@Override
	public void close() { }
}
