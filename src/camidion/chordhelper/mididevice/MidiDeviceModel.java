package camidion.chordhelper.mididevice;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import javax.sound.midi.MidiDevice;
import javax.sound.midi.MidiUnavailableException;
import javax.sound.midi.Receiver;
import javax.sound.midi.Transmitter;
import javax.swing.tree.TreePath;

/**
 * １個の{@link MidiDevice}を表すモデル
 */
public class MidiDeviceModel {
	/**
	 * {@link javax.swing.JTree}で使用するツリー表示のパスを返します。
	 * @return ツリーパス
	 */
	public TreePath getTreePath() { return treePath; }
	private TreePath treePath;
	/**
	 * このリストのMIDIデバイスの入出力タイプを返します。
	 * @return このリストのMIDIデバイスの入出力タイプ
	 */
	public MidiDeviceInOutType getInOutType() { return ioType; }
	private MidiDeviceInOutType ioType;
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
	/**
	 * このMIDIデバイスモデルを収容しているツリーモデルを返します。
	 */
	public MidiDeviceTreeModel getDeviceTreeModel() { return deviceTreeModel; }
	protected MidiDeviceTreeModel deviceTreeModel;
	/**
	 * MIDIデバイスからモデルを構築します。
	 *
	 * @param device 対象MIDIデバイス
	 * @param deviceTreeModel このモデルの親ツリー
	 */
	public MidiDeviceModel(MidiDevice device, MidiDeviceTreeModel deviceTreeModel) {
		this.deviceTreeModel = deviceTreeModel;
		ioType = MidiDeviceInOutType.getValueFor(this.device = device);
		if( device.getMaxTransmitters() != 0 ) txListModel = new TransmitterListModel(this);
		if( device.getMaxReceivers() != 0 ) rxListModel = new ReceiverListModel(this);
		treePath = new TreePath(new Object[] {deviceTreeModel, ioType ,this});
	}
	/**
	 * このMIDIデバイスを開きます。
	 * MIDIデバイスを {@link MidiDevice#open()} で開き、
	 * レシーバをサポートしている場合は {@link MidiDevice#getReceiver()} でレシーバを1個開きます。
	 *
	 * @throws MidiUnavailableException リソースの制約のためにデバイス開けない、
	 * またはレシーバを使用できない場合にスローされる。
	 */
	public void open() throws MidiUnavailableException {
		device.open();
		if( rxListModel != null ) rxListModel.openSingleReceiver();
	}
	/**
	 * 開かれている{@link Receiver}を返します。
	 * @return このMIDIデバイスの開かれた{@link Receiver}（ない場合はnull）
	 */
	public Receiver getReceiver() {
		if( rxListModel == null ) return null;
		List<Receiver> rxList = rxListModel.getTransceivers();
		return rxList.isEmpty() ? null : rxList.get(0);
	}
	/**
	 * このリストモデルの{@link Receiver}に接続された{@link Transmitter}を全て閉じ、
	 * 接続相手だったMIDIデバイスモデルのユニークな集合を返します。
	 * 接続相手が存在していなかった場合は、空集合を返します。
	 *
	 * @return 閉じた{@link Transmitter}を持っていた接続相手の{@link MidiDeviceModel}の集合
	 */
	public Set<MidiDeviceModel> disconnectPeerTransmitters() {
		return rxListModel == null ? Collections.emptySet() :
			rxListModel.closeAllConnectedTransmitters();
	}
	/**
	 * このMIDIデバイスを {@link MidiDevice#close()} で閉じます。
	 * このMIDIデバイスの{@link Receiver}に接続している相手デバイスの{@link Transmitter}があれば、
	 * それらも全て閉じます。
	 */
	public void close() { disconnectPeerTransmitters(); device.close(); }
}
