package camidion.chordhelper.mididevice;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.sound.midi.MidiDevice;
import javax.sound.midi.MidiUnavailableException;
import javax.sound.midi.Receiver;
import javax.sound.midi.Sequencer;
import javax.sound.midi.Transmitter;

/**
 * {@link Transmitter} のリストを表す {@link javax.swing.ListModel}
 */
public class TransmitterListModel extends AbstractTransceiverListModel<Transmitter> {
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
	 * 要素数が1個増えたことをこのモデルを参照しているビューへ通知します。
	 *
	 * @return 未接続の{@link Transmitter}
	 * @throws MidiUnavailableException リソースの制約のためにトランスミッタを使用できない場合にスローされる
	 */
	public Transmitter openTransmitter() throws MidiUnavailableException {
		int index = getTransceivers().size();
		Transmitter tx = deviceModel.getMidiDevice().getTransmitter();
		fireIntervalAdded(this, index, index);
		return tx;
	}
	/**
	 * 指定された{@link Transmitter}を閉じ、要素数が1個減ったことをこのモデルを参照しているビューへ通知します。
	 *
	 * @param tx このリストモデルで開いている{@link Transmitter}
	 */
	public void closeTransmitter(Transmitter tx) {
		int index = indexOf(tx);
		tx.close();
		fireIntervalRemoved(this, index, index);
	}
	/**
	 * 指定された複数の{@link Transmitter}を閉じ、要素数が大きく減ったことをこのモデルを参照しているビューへ通知します。
	 *
	 * @param txc このリストモデルで開いている{@link Transmitter}のコレクション
	 */
	public void closeTransmitters(Collection<Transmitter> txc) {
		if( txc.isEmpty() ) return;
		int length = getSize();
		for( Transmitter tx : txc ) tx.close();
		fireIntervalRemoved(this, 0, length);
	}
	/**
	 * このリストモデルにある{@link Transmitter}のうち、
	 * 引数で指定された{@link Receiver}へデータを送信しているものを探し、
	 * それらを{@link #closeTransmitters(Collection)}で閉じます。
	 *
	 * @return 閉じた{@link Transmitter}のリスト
	 */
	public List<Transmitter> closeTransmittersFor(Receiver rx) {
		List<Transmitter> txToClose = new ArrayList<Transmitter>();
		for( Transmitter tx : getTransceivers() ) if( tx.getReceiver() == rx ) txToClose.add(tx);
		closeTransmitters(txToClose);
		return txToClose;
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
		if( device instanceof Sequencer || ! device.isOpen() ) return;
		//
		// 接続状態を保存
		// 自分Tx → 相手Rx
		List<Receiver> peerRxList = new ArrayList<Receiver>();
		for( Transmitter tx : device.getTransmitters() ) {
			Receiver rx = tx.getReceiver();
			if( rx != null ) peerRxList.add(rx);
		}
		// 自分Rx ← 相手Tx
		List<Transmitter> peerTxList = new ArrayList<Transmitter>();
		List<Receiver> rxList = device.getReceivers();
		if( ! rxList.isEmpty() ) {
			for( MidiDeviceModel m : deviceModel.getDeviceTreeModel() ) {
				if( m == deviceModel ) continue; // 「自分Rx ← 自分Tx」は重複するのでスキップ
				List<Transmitter> peerSourceTxList = m.getMidiDevice().getTransmitters();
				for( Transmitter tx : peerSourceTxList ) {
					for( Receiver rx : rxList ) if( tx.getReceiver() == rx ) peerTxList.add(tx);
				}
			}
		}
		device.close(); // 一旦閉じる
		try {
			device.open(); // 再び開くことでマイクロ秒位置がリセットされる
			//
			// 接続を復元
			// 自分Tx → 相手Rx
			for( Receiver peerRx : peerRxList ) openTransmitter().setReceiver(peerRx);
			// 自分Rx ← 相手Tx
			if( ! rxList.isEmpty() ) {
				for( Transmitter peerTx : peerTxList ) peerTx.setReceiver(rxList.get(0));
			}
		} catch( MidiUnavailableException e ) {
			e.printStackTrace();
		}
	}
}
