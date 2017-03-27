package camidion.chordhelper.mididevice;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

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
	 * このリストモデルにある{@link Transmitter}のうち、
	 * 引数で指定された{@link Receiver}へデータを送信しているものがあれば、
	 * それらをすべて閉じ、要素数が大きく減ったことをこのモデルを参照しているビューへ通知します。
	 * @param rx 対象の{@link Receiver}
	 * @return 閉じた{@link Transmitter}のリスト（一つも閉じられなかった場合は空のリスト）
	 */
	public List<Transmitter> closeTransmittersFor(Receiver rx) {
		List<Transmitter> txToClose = getTransceivers().stream()
				.filter(tx -> tx.getReceiver() == rx)
				.collect(Collectors.toList());
		txToClose.forEach(tx -> tx.close());
		if( ! txToClose.isEmpty() ) fireIntervalRemoved(this, 0, getSize());
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
		//   自分Tx → 相手Rx
		List<Receiver> peerRxList = device.getTransmitters().stream()
				.map(tx -> tx.getReceiver())
				.filter(Objects::nonNull)
				.collect(Collectors.toList());
		//   自分Rx ← 相手Tx
		List<Receiver> myRxList = device.getReceivers(); // 基本的に 0件 or 1件
		List<Transmitter> peerTxList = deviceModel.getDeviceTreeModel().stream()
			.filter(peer -> peer != deviceModel)
			.flatMap(peer -> peer.getMidiDevice().getTransmitters().stream().filter(
				peerTx -> myRxList.stream().anyMatch(myRx -> myRx == peerTx.getReceiver())
			))
			.collect(Collectors.toList());
		device.close(); // 一旦閉じる
		try {
			device.open(); // 再び開くことでマイクロ秒位置がリセットされる
			// 接続を復元
			//   自分Tx → 相手Rx （例外キャッチのためあえてラムダ式にしていない）
			for( Receiver peerRx : peerRxList ) openTransmitter().setReceiver(peerRx);
			//   自分Rx ← 相手Tx
			if( ! myRxList.isEmpty() ) peerTxList.forEach(peerTx -> peerTx.setReceiver(myRxList.get(0)));
		} catch( MidiUnavailableException e ) {
			e.printStackTrace();
		}
	}
}
