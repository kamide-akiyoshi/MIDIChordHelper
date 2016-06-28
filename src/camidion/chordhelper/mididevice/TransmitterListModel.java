package camidion.chordhelper.mididevice;

import java.util.List;
import java.util.Vector;

import javax.sound.midi.MidiDevice;
import javax.sound.midi.MidiUnavailableException;
import javax.sound.midi.Receiver;
import javax.sound.midi.Sequencer;
import javax.sound.midi.Transmitter;

/**
 * {@link Transmitter} のリストを表す {@link javax.swing.ListModel}
 */
public class TransmitterListModel extends TransceiverListModel<Transmitter> {
	public TransmitterListModel(MidiDeviceModel deviceModel) { super(deviceModel); }
	protected List<Transmitter> getTransceivers() {
		return deviceModel.getMidiDevice().getTransmitters();
	}
	private Transmitter dummyTx = new DummyTransmitter();
	@Override
	public Transmitter getElementAt(int index) {
		List<Transmitter> txList = getTransceivers();
		int length = txList.size();
		if( index == length ) return dummyTx;
		if( index > length || index < 0 ) return null;
		return txList.get(index);
	}
	@Override
	public int getSize() { return super.getSize() + 1; }
	@Override
	public int indexOf(Object element) {
		List<Transmitter> txList = getTransceivers();
		return dummyTx.equals(element) ? txList.size() : txList.indexOf(element);
	}
	/**
	 * 新しい{@link Transmitter}を{@link MidiDevice#getTransmitter}で生成し、
	 * このモデルを参照しているビューに通知します。
	 *
	 * @return 未接続の{@link Transmitter}
	 * @throws MidiUnavailableException リソースの制約のためにトランスミッタを使用できない場合にスローされる
	 */
	public Transmitter openTransmitter() throws MidiUnavailableException {
		Transmitter tx = deviceModel.getMidiDevice().getTransmitter();
		fireIntervalAdded(this, 0, getSize());
		return tx;
	}
	/**
	 * 相手のMIDIデバイスが持つ最初の{@link Receiver}を、
	 * このリストモデルの新規{@link Transmitter}に接続します。
	 *
	 * @param anotherDeviceModel 接続相手のMIDIデバイス
	 * @throws MidiUnavailableException リソースの制約のためにトランスミッタを使用できない場合にスローされる
	 */
	public void connectToFirstReceiverOfDevice(MidiDeviceModel anotherDeviceModel) throws MidiUnavailableException {
		List<Receiver> rxList = anotherDeviceModel.getMidiDevice().getReceivers();
		if( rxList.isEmpty() ) return;
		deviceModel.getTransmitterListModel().openTransmitter().setReceiver(rxList.get(0));
	}
	/**
	 * 指定の{@link Transmitter}を閉じ、要素が減ったことを、
	 * このモデルを参照しているビューに通知します。
	 *
	 * @param tx このリストモデルで開いている{@link Transmitter}
	 */
	public void closeTransmitter(Transmitter tx) {
		tx.close();
		fireIntervalRemoved(this, 0, getSize());
	}
	/**
	 * このリストモデルにある{@link Transmitter}のうち、
	 * 引数で指定された{@link Receiver}へデータを送信しているものを全て閉じます。
	 * 閉じるとリストから自動的に削除されるので、表示の更新も行います。
	 */
	public void closePeerTransmitterOf(Receiver rx) {
		List<Transmitter> closingTxList = new Vector<Transmitter>();
		List<Transmitter> txList = getTransceivers();
		for( Transmitter tx : txList ) if( tx.getReceiver() == rx ) closingTxList.add(tx);
		if( closingTxList.isEmpty() ) return;
		int length = getSize();
		for( Transmitter tx : closingTxList ) tx.close();
		fireIntervalRemoved(this, 0, length);
	}
	/**
	 * マイクロ秒位置をリセットします。
	 * <p>マイクロ秒位置はMIDIデバイスを開いてからの時間で表されます。
	 * このメソッドではMIDIデバイスをいったん閉じて再び開くことによって
	 * 時間位置をリセットします。
	 * 接続相手のデバイスがあった場合、元通りに接続を復元します。
	 * </p>
	 * <p>MIDIデバイスからリアルタイムレコーディングを開始するときは、
	 * 必ずマイクロ秒位置をリセットする必要があります。
	 * （リセットされていないマイクロ秒位置がそのままシーケンサに記録されると、
	 * 大幅に後ろのほうにずれて記録されてしまいます）
	 * </p>
	 */
	public void resetMicrosecondPosition() {
		MidiDevice device = deviceModel.getMidiDevice();
		//
		// シーケンサはこのメソッドでのリセット対象外
		if( device instanceof Sequencer ) return;
		//
		// デバイスを閉じる前に接続状態を把握
		List<Receiver> peerRxList = new Vector<Receiver>();
		List<Transmitter> txList = device.getTransmitters();
		for( Transmitter tx : txList ) {
			Receiver rx = tx.getReceiver();
			if( rx != null ) peerRxList.add(rx);
		}
		List<Transmitter> peerTxList = new Vector<Transmitter>();
		MidiDeviceModelList deviceModelList = deviceModel.getDeviceModelList();
		List<Receiver> rxList = device.getReceivers();
		for( Receiver rx : rxList ) {
			for( MidiDeviceModel m : deviceModelList ) {
				if( m == deviceModel ) continue;
				List<Transmitter> peerSourceTxList = m.getMidiDevice().getTransmitters();
				for( Transmitter tx : peerSourceTxList ) if( tx.getReceiver() == rx ) peerTxList.add(tx);
			}
		}
		// いったん閉じて開く（ここでマイクロ秒位置がリセットされる）
		// その後、元通りに接続し直す
		device.close();
		try {
			device.open();
			for( Receiver peerRx : peerRxList ) openTransmitter().setReceiver(peerRx);
			if( ! rxList.isEmpty() ) {
				Receiver rx = rxList.get(0);
				for( Transmitter peerTx : peerTxList ) peerTx.setReceiver(rx);
			}
		} catch( MidiUnavailableException e ) {
			e.printStackTrace();
		}
	}
}