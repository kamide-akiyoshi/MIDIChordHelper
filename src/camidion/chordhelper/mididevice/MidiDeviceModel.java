package camidion.chordhelper.mididevice;

import java.util.List;
import java.util.Vector;

import javax.sound.midi.MidiDevice;
import javax.sound.midi.MidiUnavailableException;
import javax.sound.midi.Receiver;
import javax.sound.midi.Sequencer;
import javax.sound.midi.Transmitter;
import javax.swing.AbstractListModel;
import javax.swing.tree.TreePath;

/**
 * １個の{@link MidiDevice}を表すモデル
 */
public class MidiDeviceModel {

	/**
	 * {@link Receiver} のリストを表す {@link javax.swing.ListModel}
	 */
	public class ReceiverListModel extends AbstractListModel<Receiver> {
		@Override
		public Receiver getElementAt(int index) { return device.getReceivers().get(index); }
		@Override
		public int getSize() { return device.getReceivers().size(); }
		/**
		 * 指定の要素がこのリストモデルで最初に見つかった位置を返します。
		 *
		 * @param element 探したい要素
		 * @return 位置のインデックス（先頭が 0、見つからないとき -1）
		 */
		public int indexOf(Receiver element) { return device.getReceivers().indexOf(element); }
	}

	/**
	 * 実体のない新規{@link Transmitter}を表すインターフェース
	 */
	public interface NewTransmitter extends Transmitter {};
	/**
	 * {@link Transmitter} のリストを表す {@link javax.swing.ListModel}
	 */
	public class TransmitterListModel extends AbstractListModel<Transmitter> {
		private NewTransmitter newTransmitter = new NewTransmitter() {
			@Override
			public void setReceiver(Receiver receiver) { }
			@Override
			public Receiver getReceiver() { return null; }
			@Override
			public void close() { }
		};
		@Override
		public Transmitter getElementAt(int index) {
			List<Transmitter> txList = device.getTransmitters();
			int length = txList.size();
			if( index == length ) return newTransmitter;
			if( index > length || index < 0 ) return null;
			return txList.get(index);
		}
		@Override
		public int getSize() { return device.getTransmitters().size() + 1; }
		/**
		 * 指定の要素がこのリストモデルで最初に見つかった位置（先頭が 0）を返します。
		 * 見つからなかった場合は -1 を返します。
		 *
		 * @param element 探したい要素
		 * @return 位置のインデックス
		 */
		public int indexOf(Transmitter element) {
			List<Transmitter> txList = device.getTransmitters();
			return element.equals(newTransmitter) ? txList.size() : txList.indexOf(element);
		}
		/**
		 * レシーバに未接続の最初の{@link Transmitter}を開いて返します。
		 * ない場合は {@link MidiDevice#getTransmitter} で新たに取得して返します。
		 *
		 * @return 新しく開かれた未接続の{@link Transmitter}
		 */
		public Transmitter openTransmitter() {
			List<Transmitter> txList = device.getTransmitters();
			for( Transmitter tx : txList ) if( tx.getReceiver() == null ) return tx;
			Transmitter tx;
			try {
				tx = device.getTransmitter();
			} catch( MidiUnavailableException e ) {
				e.printStackTrace();
				return null;
			}
			fireIntervalAdded(this, 0, getSize());
			return tx;
		}
		/**
		 * このリストモデルで開いている指定の{@link Transmitter}があれば、
		 * それを閉じて表示を更新します。
		 * ない場合は無視されます。
		 * @param txToClose このリストモデルで開いている{@link Transmitter}
		 */
		public void closeTransmitter(Transmitter txToClose) {
			if( ! device.getTransmitters().contains(txToClose) ) return;
			txToClose.close();
			fireIntervalRemoved(this, 0, getSize());
		}
	}

	/**
	 * このリストのMIDIデバイスの入出力タイプを返します。
	 * @return このリストのMIDIデバイスの入出力タイプ
	 */
	public MidiDeviceInOutType getMidiDeviceInOutType() { return ioType; }
	private MidiDeviceInOutType ioType;

	/**
	 * {@link javax.swing.JTree}で使用するツリー表示のパスを返します。
	 * @return ツリーパス
	 */
	public TreePath getTreePath() { return treePath; }
	private TreePath treePath;

	/**
	 * 対象MIDIデバイスを返します。
	 * @return 対象MIDIデバイス
	 */
	public MidiDevice getMidiDevice() { return device; }
	protected MidiDevice device;
	/**
	 * 対象MIDIデバイスの名前を返します。
	 */
	@Override
	public String toString() { return device.getDeviceInfo().toString(); }
	/**
	 * このデバイスモデルが {@link Transmitter} をサポートしているか調べます。
	 * @return {@link Transmitter} をサポートしていたら true
	 */
	public boolean txSupported() { return device.getMaxTransmitters() != 0; }
	/**
	 * このデバイスモデルが {@link Receiver} をサポートしているか調べます。
	 * @return {@link Receiver} をサポートしていたら true
	 */
	public boolean rxSupported() { return device.getMaxReceivers() != 0; }

	/**
	 * {@link Transmitter} のリストモデルを返します。サポートしていない場合はnullを返します。
	 * @return リストモデルまたはnull
	 */
	public TransmitterListModel getTransmitterList() { return transmitterList; }
	private TransmitterListModel transmitterList;
	/**
	 * {@link Receiver} のリストモデルを返します。サポートしていない場合はnullを返します。
	 * @return リストモデルまたはnull
	 */
	public ReceiverListModel getReceiverList() { return receiverList; }
	private ReceiverListModel receiverList;

	protected MidiDeviceModelList deviceModelList;
	/**
	 * MIDIデバイスモデルを構築します。
	 *
	 * @param device 対象MIDIデバイス
	 * @param deviceModelList 接続相手となりうるMIDIデバイスのリスト
	 */
	public MidiDeviceModel(MidiDevice device, MidiDeviceModelList deviceModelList) {
		this.device = device;
		this.deviceModelList = deviceModelList;
		if( txSupported() ) {
			transmitterList = new TransmitterListModel();
		}
		if( rxSupported() ) {
			ioType = txSupported() ? MidiDeviceInOutType.MIDI_IN_OUT :MidiDeviceInOutType.MIDI_OUT;
			receiverList = new ReceiverListModel();
		}
		else {
			ioType = txSupported() ? MidiDeviceInOutType.MIDI_IN :MidiDeviceInOutType.MIDI_NONE;
		}
		treePath = new TreePath(new Object[] {deviceModelList, ioType ,this});
	}
	/**
	 * 未接続の{@link Transmitter}を、引数で指定されたリストモデルの最初の{@link Receiver}に接続します。
	 * @param anotherDeviceModel 接続可能な{@link Receiver}を持つリストモデル
	 */
	public void connectToReceiverOf(MidiDeviceModel anotherDeviceModel) {
		if( ! txSupported() || anotherDeviceModel == null || ! anotherDeviceModel.rxSupported() ) return;
		List<Receiver> rxList = anotherDeviceModel.device.getReceivers();
		if( rxList.isEmpty() ) return;
		transmitterList.openTransmitter().setReceiver(rxList.get(0));
	}
	/**
	 * {@link Receiver}を1個だけ開きます。サポートしていない場合は無視されます。
	 *
	 * @throws MidiUnavailableException デバイスを開くことができない場合
	 */
	public void openReceiver() throws MidiUnavailableException {
		if( rxSupported() && device.getReceivers().isEmpty() ) device.getReceiver();
	}
	/**
	 * {@link MidiDevice}を閉じる前に、{@link Receiver}を閉じます。
	 *
	 * 閉じようとしている{@link Receiver}を他デバイスの{@link Transmitter}が使用していた場合は、
	 * その{@link Transmitter}も閉じます。
	 */
	public void closeReceiver() {
		List<Receiver> rxList = device.getReceivers();
		for( Receiver rx : rxList ) {
			for( MidiDeviceModel m : deviceModelList ) {
				if( m == this || ! m.txSupported() ) continue;
				for( int i=0; i<m.transmitterList.getSize(); i++ ) {
					Transmitter tx = m.transmitterList.getElementAt(i);
					if( tx.getReceiver() == rx ) m.transmitterList.closeTransmitter(tx);
				}
			}
			rx.close();
		}
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
		if( ! txSupported() || device instanceof Sequencer ) return;
		//
		// デバイスを閉じる前に接続相手の情報を保存
		List<Transmitter> myTxList = device.getTransmitters();
		List<Receiver> peerRxList = new Vector<Receiver>();
		Receiver rx;
		for( Transmitter tx : myTxList ) if( (rx = tx.getReceiver()) != null ) peerRxList.add(rx);
		List<Transmitter> peerTxList = null;
		if( rxSupported() ) {
			rx = device.getReceivers().get(0);
			peerTxList = new Vector<Transmitter>();
			for( MidiDeviceModel m : deviceModelList ) {
				if( m == this || ! m.txSupported() ) continue;
				for( int i=0; i<m.transmitterList.getSize(); i++ ) {
					Transmitter tx = m.transmitterList.getElementAt(i);
					if( tx.getReceiver() == rx ) peerTxList.add(tx);
				}
			}
		}
		// いったん閉じて開く（ここでマイクロ秒位置がリセットされる）
		device.close();
		try {
			device.open();
		} catch( MidiUnavailableException e ) {
			e.printStackTrace();
		}
		// 元通りに接続し直す
		for( Receiver peerRx : peerRxList ) {
			Transmitter tx = transmitterList.openTransmitter();
			if( tx != null ) tx.setReceiver(peerRx);
		}
		if( peerTxList != null ) {
			rx = device.getReceivers().get(0);
			for( Transmitter peerTx : peerTxList ) peerTx.setReceiver(rx);
		}
	}
}