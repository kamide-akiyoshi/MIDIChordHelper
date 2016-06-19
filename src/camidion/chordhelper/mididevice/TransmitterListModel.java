package camidion.chordhelper.mididevice;

import java.util.List;
import java.util.Vector;

import javax.sound.midi.MidiDevice;
import javax.sound.midi.MidiUnavailableException;
import javax.sound.midi.Receiver;
import javax.sound.midi.Sequencer;
import javax.sound.midi.Transmitter;
import javax.swing.AbstractListModel;

/**
 * {@link Transmitter} のリストを表す {@link javax.swing.ListModel}
 */
public class TransmitterListModel extends AbstractListModel<Transmitter> {
	protected Transmitter dummyTransmitter = new DummyTransmitter();
	protected MidiDeviceModel deviceModel;
	public TransmitterListModel(MidiDeviceModel deviceModel) {
		this.deviceModel = deviceModel;
	}
	@Override
	public Transmitter getElementAt(int index) {
		List<Transmitter> txList = deviceModel.getMidiDevice().getTransmitters();
		int length = txList.size();
		if( index == length ) return dummyTransmitter;
		if( index > length || index < 0 ) return null;
		return txList.get(index);
	}
	@Override
	public int getSize() {
		return deviceModel.getMidiDevice().getTransmitters().size() + 1;
	}
	public int indexOf(Object element) {
		List<Transmitter> txList = deviceModel.getMidiDevice().getTransmitters();
		return dummyTransmitter.equals(element) ? txList.size() : txList.indexOf(element);
	}
	/**
	 * レシーバに未接続の最初の{@link Transmitter}を返します。
	 * ない場合は{@link MidiDevice#getTransmitter}で新たに取得して返します。
	 *
	 * @return 未接続の{@link Transmitter}
	 * @throws MidiUnavailableException リソースの制約のためにトランスミッタを使用できない場合にスローされる
	 */
	public Transmitter getUnconnectedTransmitter() throws MidiUnavailableException {
		MidiDevice device = deviceModel.getMidiDevice();
		List<Transmitter> txList = device.getTransmitters();
		for( Transmitter tx : txList ) if( tx.getReceiver() == null ) return tx;
		Transmitter tx;
		tx = device.getTransmitter();
		fireIntervalAdded(this, 0, getSize());
		return tx;
	}
	/**
	 * このリストモデルの{@link #getUnconnectedTransmitter()}が返した{@link Transmitter}を、
	 * 相手のMIDIデバイスが持つ最初の{@link Receiver}に接続します。
	 *
	 * @param anotherDeviceModel 接続相手のMIDIデバイス
	 * @throws MidiUnavailableException リソースの制約のためにトランスミッタを使用できない場合にスローされる
	 */
	public void connectToFirstReceiverOfDevice(MidiDeviceModel anotherDeviceModel) throws MidiUnavailableException {
		List<Receiver> rxList = anotherDeviceModel.getMidiDevice().getReceivers();
		if( ! rxList.isEmpty() )
			deviceModel.getTransmitterListModel().getUnconnectedTransmitter().setReceiver(rxList.get(0));
	}
	/**
	 * 指定の{@link Transmitter}がリストにあれば、それを閉じます。
	 * 閉じるとリストから自動的に削除されるので、表示の更新も行います。
	 *
	 * @param tx このリストモデルで開いている{@link Transmitter}
	 */
	public void close(Transmitter tx) {
		if( ! deviceModel.getMidiDevice().getTransmitters().contains(tx) ) return;
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
		List<Transmitter> txList = deviceModel.getMidiDevice().getTransmitters();
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
		List<Receiver> myRxList = device.getReceivers();
		List<Transmitter> peerTxList = new Vector<Transmitter>();
		MidiDeviceModelList deviceModelList = deviceModel.getDeviceModelList();
		for( Receiver rx : myRxList ) {
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
			for( Receiver peerRx : peerRxList ) getUnconnectedTransmitter().setReceiver(peerRx);
			if( ! myRxList.isEmpty() ) {
				Receiver rx = myRxList.get(0);
				for( Transmitter peerTx : peerTxList ) peerTx.setReceiver(rx);
			}
		} catch( MidiUnavailableException e ) {
			e.printStackTrace();
		}
	}
}