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
		public int indexOf(Object element) { return device.getReceivers().indexOf(element); }
		/**
		 * {@link MidiDevice#getReceiver()} でレシーバを1個開きます。
		 * すでに1個開いている場合は無視されます。
		 *
		 * @throws MidiUnavailableException リソースの制約のためにレシーバを使用できない場合にスローされる
		 */
		public void openReceiver() throws MidiUnavailableException {
			if( device.getReceivers().isEmpty() ) device.getReceiver();
		}
		/**
		 * このリストモデルの{@link Receiver}に接続された他デバイスの{@link Transmitter}を全て閉じます。
		 */
		public void closePeerTransmitters() {
			List<Receiver> rxList = device.getReceivers();
			for( Receiver rx : rxList ) {
				for( MidiDeviceModel m : deviceModelList ) {
					if( m == MidiDeviceModel.this || m.txListModel == null ) continue;
					m.txListModel.closePeerTransmitterOf(rx);
				}
			}
		}
	}

	/**
	 * 実体のない新規{@link Transmitter}を表すインターフェース
	 */
	interface NewTransmitter extends Transmitter {};
	/**
	 * {@link Transmitter} のリストを表す {@link javax.swing.ListModel}
	 */
	public class TransmitterListModel extends AbstractListModel<Transmitter> {
		private NewTransmitter newTransmitter = new NewTransmitter() {
			private Receiver receiver;
			@Override
			public void setReceiver(Receiver receiver) { this.receiver = receiver; }
			@Override
			public Receiver getReceiver() { return receiver; }
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
		 * @return 位置
		 */
		public int indexOf(Object element) {
			List<Transmitter> txList = device.getTransmitters();
			return element.equals(newTransmitter) ? txList.size() : txList.indexOf(element);
		}
		/**
		 * レシーバに未接続の最初の{@link Transmitter}を返します。
		 * ない場合は{@link MidiDevice#getTransmitter}で新たに取得して返します。
		 *
		 * @return 未接続の{@link Transmitter}
		 * @throws MidiUnavailableException リソースの制約のためにトランスミッタを使用できない場合にスローされる
		 */
		public Transmitter getUnconnectedTransmitter() throws MidiUnavailableException {
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
			List<Receiver> rxList = anotherDeviceModel.device.getReceivers();
			if( ! rxList.isEmpty() ) txListModel.getUnconnectedTransmitter().setReceiver(rxList.get(0));
		}
		/**
		 * 指定の{@link Transmitter}がリストにあれば、それを閉じます。
		 * 閉じるとリストから自動的に削除されるので、表示の更新も行います。
		 *
		 * @param tx このリストモデルで開いている{@link Transmitter}
		 */
		public void close(Transmitter tx) {
			if( ! device.getTransmitters().contains(tx) ) return;
			tx.close();
			fireIntervalRemoved(this, 0, getSize());
		}
		/**
		 * このリストモデルにある{@link Transmitter}のうち、
		 * 引数で指定された{@link Receiver}へデータを送信しているものを全て閉じます。
		 * 閉じるとリストから自動的に削除されるので、表示の更新も行います。
		 */
		private void closePeerTransmitterOf(Receiver rx) {
			List<Transmitter> originalTxList = device.getTransmitters();
			List<Transmitter> closingTxList = new Vector<Transmitter>();
			for( Transmitter tx : originalTxList ) if( tx.getReceiver() == rx ) closingTxList.add(tx);
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
			//
			// シーケンサはこのメソッドでのリセット対象外
			if( device instanceof Sequencer ) return;
			//
			// デバイスを閉じる前に接続状態を把握
			List<Transmitter> myTxList = device.getTransmitters();
			List<Receiver> peerRxList = new Vector<Receiver>();
			for( Transmitter tx : myTxList ) {
				Receiver rx = tx.getReceiver();
				if( rx != null ) peerRxList.add(rx);
			}
			List<Receiver> myRxList = device.getReceivers();
			List<Transmitter> peerTxList = new Vector<Transmitter>();
			for( Receiver rx : myRxList ) {
				for( MidiDeviceModel m : deviceModelList ) {
					if( m == MidiDeviceModel.this ) continue;
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
	public TransmitterListModel getTransmitterListModel() { return txListModel; }
	private TransmitterListModel txListModel;
	/**
	 * {@link Receiver} のリストモデルを返します。サポートしていない場合はnullを返します。
	 * @return リストモデルまたはnull
	 */
	public ReceiverListModel getReceiverListModel() { return rxListModel; }
	private ReceiverListModel rxListModel;

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
			txListModel = new TransmitterListModel();
		}
		if( rxSupported() ) {
			ioType = txSupported() ? MidiDeviceInOutType.MIDI_IN_OUT :MidiDeviceInOutType.MIDI_OUT;
			rxListModel = new ReceiverListModel();
		}
		else {
			ioType = txSupported() ? MidiDeviceInOutType.MIDI_IN :MidiDeviceInOutType.MIDI_NONE;
		}
		treePath = new TreePath(new Object[] {deviceModelList, ioType ,this});
	}
	/**
	 * このMIDIデバイスモデルを開きます。
	 * MIDIデバイスを {@link MidiDevice#open()} で開き、
	 * レシーバをサポートしている場合は {@link MidiDevice#getReceiver()} でレシーバを1個開きます。
	 *
	 * @throws MidiUnavailableException リソースの制約のためにデバイス開けない、
	 * またはレシーバを使用できない場合にスローされる。
	 */
	public void open() throws MidiUnavailableException {
		device.open();
		if( rxListModel != null ) rxListModel.openReceiver();
	}
	/**
	 * このMIDIデバイスを {@link MidiDevice#close()} で閉じます。
	 * このMIDIデバイスの{@link Receiver}に接続している他デバイスの{@link Transmitter}があれば、
	 * それらも全て閉じます。
	 */
	public void close() {
		if( rxListModel != null ) rxListModel.closePeerTransmitters();
		device.close();
	}
}
