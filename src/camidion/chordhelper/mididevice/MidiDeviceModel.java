package camidion.chordhelper.mididevice;

import javax.sound.midi.MidiDevice;
import javax.sound.midi.MidiSystem;
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
	 * MIDIデバイス情報からMIDIデバイスモデルを構築します。
	 *
	 * @param deviceInfo 対象MIDIデバイス情報
	 * @param deviceTreeModel 収容先のMIDIデバイスツリーモデル
	 * @throws MidiUnavailableException {@link MidiSystem#getMidiDevice(MidiDevice.Info)}からの例外
	 */
	public MidiDeviceModel(MidiDevice.Info deviceInfo, MidiDeviceTreeModel deviceTreeModel) throws MidiUnavailableException {
		this(MidiSystem.getMidiDevice(deviceInfo), deviceTreeModel);
	}
	/**
	 * MIDIデバイスからモデルを構築します。
	 *
	 * @param device 対象MIDIデバイス
	 * @param deviceTreeModel このモデルの親ツリー
	 */
	public MidiDeviceModel(MidiDevice device, MidiDeviceTreeModel deviceTreeModel) {
		this.deviceTreeModel = deviceTreeModel;
		this.device = device;
		if( device.getMaxTransmitters() != 0 ) txListModel = new TransmitterListModel(this);
		if( device.getMaxReceivers() != 0 ) rxListModel = new ReceiverListModel(this);
		ioType = MidiDeviceInOutType.getValueFor(device);
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
	 * このMIDIデバイスを {@link MidiDevice#close()} で閉じます。
	 * このMIDIデバイスの{@link Receiver}に接続している他デバイスの{@link Transmitter}があれば、
	 * それらも全て閉じます。
	 */
	public void close() {
		if( rxListModel != null ) rxListModel.closeTransmitters();
		device.close();
	}
}
