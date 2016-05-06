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
 * １個の{@link MidiDevice}で開かれている{@link Transmitter}/{@link Receiver}のリストモデル
 */
public class MidiConnecterListModel extends AbstractListModel<AutoCloseable> {
	protected MidiDevice device;
	private MidiDeviceModelList deviceModelList;
	private MidiDeviceInOutType ioType;
	private TreePath treePath;
	/**
	 * 実体のない新規{@link Transmitter}を表すインターフェース
	 */
	public interface NewTransmitter extends Transmitter {};
	public NewTransmitter newTransmitter;
	/**
	 * 指定のMIDIデバイスに属する {@link Transmitter}/{@link Receiver} のリストモデルを構築します。
	 *
	 * @param device 対象MIDIデバイス
	 * @param deviceModelList 接続相手となりうるMIDIデバイスのリスト
	 */
	public MidiConnecterListModel(MidiDevice device, MidiDeviceModelList deviceModelList) {
		this.device = device;
		this.deviceModelList = deviceModelList;
		if( txSupported() ) {
			newTransmitter = new NewTransmitter() {
				@Override
				public void setReceiver(Receiver receiver) { }
				@Override
				public Receiver getReceiver() { return null; }
				@Override
				public void close() { }
			};
		}
		ioType = rxSupported() ?
			(txSupported() ? MidiDeviceInOutType.MIDI_IN_OUT : MidiDeviceInOutType.MIDI_OUT) :
			(txSupported() ? MidiDeviceInOutType.MIDI_IN     : MidiDeviceInOutType.MIDI_NONE);
		treePath = new TreePath(new Object[] {MidiDeviceModelList.TITLE, ioType ,this});
	}
	/**
	 * 対象MIDIデバイスを返します。
	 * @return 対象MIDIデバイス
	 */
	public MidiDevice getMidiDevice() { return device; }
	/**
	 * {@link javax.swing.JTree}で使用するツリー表示のパスを返します。
	 * @return ツリーパス
	 */
	public TreePath getTreePath() { return treePath; }
	/**
	 * 対象MIDIデバイスの名前を返します。
	 */
	@Override
	public String toString() { return device.getDeviceInfo().toString(); }
	@Override
	public AutoCloseable getElementAt(int index) {
		List<Receiver> rxList = device.getReceivers();
		int rxSize = rxList.size();
		if( index < rxSize ) return rxList.get(index);
		index -= rxSize;
		List<Transmitter> txList = device.getTransmitters();
		int txSize = txList.size();
		if( index < txSize ) return txList.get(index);
		if( index == txSize ) return newTransmitter;
		return null;
	}
	@Override
	public int getSize() {
		int txSize = txSupported() ? device.getTransmitters().size() + 1 : 0;
		return device.getReceivers().size() + txSize;
	}
	/**
	 * 指定の要素がこのリストモデルで最初に見つかった位置を返します。
	 *
	 * @param element 探したい要素
	 * @return 位置のインデックス（先頭が 0、見つからないとき -1）
	 */
	public int indexOf(AutoCloseable element) {
		int index;
		List<Receiver> rxList = device.getReceivers();
		if( (index = rxList.indexOf(element)) >= 0 ) return index;
		List<Transmitter> txList = device.getTransmitters();
		if( (index = txList.indexOf(element)) >= 0 ) return rxList.size() + index;
		if( element.equals(newTransmitter) ) return rxList.size() + txList.size();
		return -1;
	}
	/**
	 * このリストが {@link Transmitter} をサポートしているか調べます。
	 * @return {@link Transmitter} をサポートしていたら true
	 */
	public boolean txSupported() { return device.getMaxTransmitters() != 0; }
	/**
	 * このリストが {@link Receiver} をサポートしているか調べます。
	 * @return {@link Receiver} をサポートしていたら true
	 */
	public boolean rxSupported() { return device.getMaxReceivers() != 0; }
	/**
	 * このリストのMIDIデバイスの入出力タイプを返します。
	 * @return このリストのMIDIデバイスの入出力タイプ
	 */
	public MidiDeviceInOutType getMidiDeviceInOutType() { return ioType; }
	/**
	 * 未接続の{@link Transmitter}を、引数で指定されたリストモデルの最初の{@link Receiver}に接続します。
	 * @param anotherModel 接続可能な{@link Receiver}を持つリストモデル
	 */
	public void connectToReceiverOf(MidiConnecterListModel anotherModel) {
		if( ! txSupported() || anotherModel == null || ! anotherModel.rxSupported() ) return;
		List<Receiver> rxList = anotherModel.device.getReceivers();
		if( rxList.isEmpty() ) return;
		openTransmitter().setReceiver(rxList.get(0));
	}
	/**
	 * レシーバに未接続の最初の{@link Transmitter}を開いて返します。
	 * ない場合は {@link MidiDevice#getTransmitter} で新たに取得して返します。
	 *
	 * @return 新しく開かれた未接続の{@link Transmitter}
	 */
	public Transmitter openTransmitter() {
		if( ! txSupported() ) return null;
		List<Transmitter> txList = device.getTransmitters();
		for( Transmitter tx : txList ) if( tx.getReceiver() == null ) return tx;
		Transmitter tx;
		try {
			tx = device.getTransmitter();
		} catch( MidiUnavailableException e ) {
			e.printStackTrace();
			return null;
		}
		fireIntervalAdded(this,0,getSize());
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
		fireIntervalRemoved(this,0,getSize());
	}
	/**
	 * 対象MIDIデバイスを開きます。
	 * @throws MidiUnavailableException デバイスを開くことができない場合
	 */
	public void openDevice() throws MidiUnavailableException {
		device.open();
		if( rxSupported() && device.getReceivers().isEmpty() ) device.getReceiver();
	}
	/**
	 * 対象MIDIデバイスを閉じます。
	 * 対象MIDIデバイスの{@link Receiver}を設定した他デバイスの{@link Transmitter}があれば、
	 * それも閉じます。
	 */
	public void closeDevice() {
		if( rxSupported() ) {
			Receiver rx = device.getReceivers().get(0);
			for( MidiConnecterListModel m : deviceModelList ) {
				if( m == this || ! m.txSupported() ) continue;
				for( int i=0; i<m.getSize(); i++ ) {
					AutoCloseable ac = m.getElementAt(i);
					if( ! (ac instanceof Transmitter) ) continue;
					Transmitter tx = ((Transmitter)ac);
					if( tx.getReceiver() == rx ) m.closeTransmitter(tx);
				}
			}
		}
		device.close();
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
		List<Transmitter> txList = device.getTransmitters();
		List<Receiver> peerRxList = new Vector<Receiver>();
		for( Transmitter tx : txList ) {
			Receiver rx = tx.getReceiver();
			if( rx != null ) peerRxList.add(rx);
		}
		List<Transmitter> peerTxList = null;
		Receiver rx = null;
		if( rxSupported() ) {
			rx = device.getReceivers().get(0);
			peerTxList = new Vector<Transmitter>();
			for( MidiConnecterListModel m : deviceModelList ) {
				if( m == this || ! m.txSupported() ) continue;
				for( int i=0; i<m.getSize(); i++ ) {
					Object obj = m.getElementAt(i);
					if( ! (obj instanceof Transmitter) ) continue;
					Transmitter tx = ((Transmitter)obj);
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
			Transmitter tx = openTransmitter();
			if( tx != null ) tx.setReceiver(peerRx);
		}
		if( peerTxList != null ) {
			rx = device.getReceivers().get(0);
			for( Transmitter peerTx : peerTxList ) peerTx.setReceiver(rx);
		}
	}
}