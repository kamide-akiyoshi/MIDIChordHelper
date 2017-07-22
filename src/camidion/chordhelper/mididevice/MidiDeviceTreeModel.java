package camidion.chordhelper.mididevice;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.sound.midi.MidiDevice;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.MidiUnavailableException;
import javax.sound.midi.Receiver;
import javax.sound.midi.Sequencer;
import javax.sound.midi.Synthesizer;
import javax.sound.midi.Transmitter;
import javax.swing.JOptionPane;
import javax.swing.event.EventListenerList;
import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeModelListener;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;

import camidion.chordhelper.ChordHelperApplet;

/**
 * 仮想MIDIデバイスを含めたすべての{@link MidiDeviceModel}をリスト構造、ツリー構造で管理するモデル。
 * 読み取り専用のMIDIデバイスリストとしても、I/Oタイプで分類されたMIDIデバイスツリーモデルとしても参照できます。
 */
public class MidiDeviceTreeModel extends AbstractList<MidiDeviceModel> implements TreeModel {
	@Override
	public String toString() { return "MIDI devices"; }

	protected List<MidiDeviceModel> deviceModelList = new ArrayList<>();

	@Override
	public int size() { return deviceModelList.size(); }
	@Override
	public MidiDeviceModel get(int index) { return deviceModelList.get(index); }

	protected Map<MidiDeviceInOutType, List<MidiDeviceModel>> deviceModelTree
		= MidiDeviceInOutType.stream().collect(Collectors.toMap(Function.identity(), t-> new ArrayList<>()));

	protected MidiDeviceModel add(MidiDevice device) {
		if( device == null ) return null;
		MidiDeviceModel dm;
		if( device instanceof Sequencer ) {
			dm = new MidiSequencerModel((Sequencer)device,this);
		} else {
			dm = new MidiDeviceModel(device,this);
		}
		deviceModelList.add(dm);
		deviceModelTree.get(dm.getInOutType()).add(dm);
		return dm;
	}
	protected MidiSequencerModel add(Sequencer sequencer) {
		return (MidiSequencerModel)add((MidiDevice)sequencer);
	}
	@Override
	public Object getRoot() { return this; }
	@Override
	public int getChildCount(Object parent) {
		if( parent == getRoot() ) return MidiDeviceInOutType.values().length - 1;
		if( parent instanceof MidiDeviceInOutType ) return deviceModelTree.get(parent).size();
		return 0;
	}
	@Override
	public Object getChild(Object parent, int index) {
		if( parent == getRoot() ) return MidiDeviceInOutType.values()[index + 1];
		if( parent instanceof MidiDeviceInOutType ) return deviceModelTree.get(parent).get(index);
		return null;
	}
	@Override
	public int getIndexOfChild(Object parent, Object child) {
		if( parent == getRoot() && child instanceof MidiDeviceInOutType ) {
			return ((MidiDeviceInOutType)child).ordinal() - 1;
		}
		if( parent instanceof MidiDeviceInOutType ) return deviceModelTree.get(parent).indexOf(child);
		return -1;
	}
	@Override
	public boolean isLeaf(Object node) { return node instanceof MidiDeviceModel; }
	@Override
	public void valueForPathChanged(TreePath path, Object newValue) {}
	@Override
	public void addTreeModelListener(TreeModelListener listener) {
		listenerList.add(TreeModelListener.class, listener);
	}
	@Override
	public void removeTreeModelListener(TreeModelListener listener) {
		listenerList.remove(TreeModelListener.class, listener);
	}
	protected EventListenerList listenerList = new EventListenerList();
	protected void fireTreeStructureChanged(Object source, Object[] path, int[] childIndices, Object[] children) {
		Object[] listeners = listenerList.getListenerList();
		for (int i = listeners.length-2; i>=0; i-=2) {
			if (listeners[i]==TreeModelListener.class) {
				((TreeModelListener)listeners[i+1]).treeStructureChanged(
					new TreeModelEvent(source,path,childIndices,children)
				);
			}
		}
	}

	private static List<MidiDevice.Info> getMidiDeviceInfo() {
		return Arrays.asList(MidiSystem.getMidiDeviceInfo());
	}
	private static MidiDevice getMidiDevice(MidiDevice.Info info) {
		try {
			return MidiSystem.getMidiDevice(info);
		} catch( Exception ex ) {
			String title = ChordHelperApplet.VersionInfo.NAME;
			String message = "MIDI device '" + info + "' unavailable\n" + ex;
			JOptionPane.showMessageDialog(null, message, title, JOptionPane.ERROR_MESSAGE);
			return null;
		}
	}
	private static Sequencer getSequencer() {
		try {
			return MidiSystem.getSequencer(false);
		} catch( MidiUnavailableException ex ) {
			String title = ChordHelperApplet.VersionInfo.NAME;
			String message = "MIDI sequencer unavailable\n" + ex;
			JOptionPane.showMessageDialog(null, message, title, JOptionPane.ERROR_MESSAGE);
		}
		return null;
	}
	private static Synthesizer getSynthesizer() {
		try {
			return MidiSystem.getSynthesizer();
		} catch( MidiUnavailableException e ) {
			String title = ChordHelperApplet.VersionInfo.NAME;
			String message = "Java internal MIDI synthesizer unavailable\n" + e;
			JOptionPane.showMessageDialog(null, message, title, JOptionPane.ERROR_MESSAGE);
		}
		return null;
	}
	private static boolean openMidiDeviceModel(MidiDeviceModel deviceModel) {
		if( Objects.isNull(deviceModel) ) return false;
		try {
			deviceModel.open(); return true;
		} catch(Exception e) {
			String title = ChordHelperApplet.VersionInfo.NAME;
			String message = "Cannot open MIDI device '"+deviceModel+"'\n"
				+ "MIDIデバイス "+deviceModel+" を開くことができません。\n\n" + e;
			JOptionPane.showMessageDialog(null, message, title, JOptionPane.ERROR_MESSAGE);
		}
		return false;
	}

	/**
	 * このMIDIデバイスツリーモデルに登録されているMIDIシーケンサーモデルを返します。
	 * @return MIDIシーケンサーモデル
	 */
	public MidiSequencerModel getSequencerModel() { return sequencerModel; }
	protected MidiSequencerModel sequencerModel;
	/**
	 * 引数で与えられたGUI仮想MIDIデバイスと、{@link #getMidiDeviceInfo()}から取得したMIDIデバイス情報から、
	 * MIDIデバイスツリーモデルを初期構築します。
	 *
	 * @param guiVirtualDevice 管理対象に含めるGUI仮想MIDIデバイス
	 */
	public MidiDeviceTreeModel(VirtualMidiDevice guiVirtualDevice) {
		MidiDeviceModel synthModel = null;
		MidiDeviceModel firstMidiInModel = null;
		MidiDeviceModel firstMidiOutModel = null;
		MidiDeviceModel guiModel = add(guiVirtualDevice);
		sequencerModel = add(getSequencer());
		for( MidiDevice device : getMidiDeviceInfo().stream()
				.map(info->getMidiDevice(info))
				.filter(Objects::nonNull)
				.filter(d -> !(d instanceof Sequencer))
				.collect(Collectors.toList())
		) {
			if( device instanceof Synthesizer ) {
				synthModel = add(getSynthesizer());
				continue;
			}
			MidiDeviceModel m = add(device);
			//
			// 最初の MIDI OUT（Windowsの場合は通常、内蔵音源 Microsoft GS Wavetable SW Synth）
			if( firstMidiOutModel == null && m.getReceiverListModel() != null ) {
				firstMidiOutModel = m;
			}
			// 最初の MIDI IN（USB MIDI インターフェースにつながったMIDIキーボードなど）
			if( firstMidiInModel == null && m.getTransmitterListModel() != null ) {
				firstMidiInModel = m;
			}
		}
		List<MidiDeviceModel> openedDeviceModels = Stream.of(
			//   NOTE: 必ず MIDI OUT Rx デバイスを先に開くこと。
			//
			//   そうすれば、後から開いた MIDI IN Tx デバイスからのタイムスタンプのほうが「若く」なるので、
			//   相手の MIDI OUT Rx デバイスは「若いころの信号が遅れてやってきた」と認識、
			//	  遅れを取り戻そうとして即座に音を出してくれる。
			//
			//   開く順序が逆になると「進みすぎるから遅らせよう」として無用なレイテンシーが発生する原因になる。
			synthModel, firstMidiOutModel,	// MIDI OUT Rx
			sequencerModel, guiModel,		// Both
			firstMidiInModel				// MIDI IN Tx
		).filter(dm->openMidiDeviceModel(dm)).collect(Collectors.toList());
		//
		// 開いたデバイスを相互に接続する。
		// 自身のTx/Rx同士の接続は、シーケンサーモデルはなし、それ以外（GUIデバイスモデル）はあり。
		connectDevices(
			openedDeviceModels.stream().filter(
				rxm->Objects.nonNull(rxm.getReceiverListModel())
			).collect(Collectors.toMap(
				Function.identity(),
				rxm->openedDeviceModels.stream()
				.filter(txm->Objects.nonNull(txm.getTransmitterListModel()))
				.filter(txm-> !(txm == sequencerModel && txm == rxm))
				.collect(Collectors.toList())
			))
		);
	}
	/**
	 * {@link Transmitter}を持つすべてのデバイス（例：MIDIキーボードなど）について、
	 * {@link MidiDeviceModel#resetMicrosecondPosition()}でマイクロ秒位置をリセットします。
	 */
	public void resetMicrosecondPosition() {
		stream().map(m->m.getTransmitterListModel()).filter(Objects::nonNull)
			.forEach(tlm->tlm.resetMicrosecondPosition());
	}
	/**
	 * MIDIデバイス間の接続をすべて切断します。
	 * 各{@link Receiver}ごとに相手デバイスの{@link Transmitter}を閉じながら、
	 * 閉じる前の接続状態をマップに保存し、そのマップを返します。
	 *
	 * @return MIDIデバイスモデル接続マップ（再接続時に{@link #connectDevices(Map)}に指定可）
	 * <ul>
	 * <li>キー：各{@link Receiver}を持つMIDIデバイスモデル</li>
	 * <li>値：接続相手だった{@link Transmitter}を持つMIDIデバイスモデルのコレクション</li>
	 * </ul>
	 */
	private Map<MidiDeviceModel, Collection<MidiDeviceModel>> disconnectAllDevices() {
		return stream().collect(
			Collectors.toMap(Function.identity(), m->m.disconnectPeerTransmitters())
		);
	}
	/**
	 * 指定された接続マップに従ってMIDIデバイス間を接続します。
	 *
	 * @param rxToTxConnections {@link #disconnectAllDevices()}
	 * が返した（あるいはそれと同じ形式の）MIDIデバイスモデル接続マップ
	 * <ul>
	 * <li>キー：{@link Receiver}側デバイスモデル</li>
	 * <li>値：{@link Transmitter}側デバイスモデルのコレクション</li>
	 * </ul>
	 */
	private void connectDevices(Map<MidiDeviceModel, Collection<MidiDeviceModel>> rxToTxConnections) {
		rxToTxConnections.entrySet().stream().forEach(rxe->{
			MidiDeviceModel rxm = rxe.getKey();
			Receiver rx = rxm.getReceiver();
			if( rx == null ) return;
			rxe.getValue().stream().filter(Objects::nonNull).forEach(txm->{
				try {
					txm.getTransmitterListModel().openTransmitter().setReceiver(rx);
				} catch( Exception ex ) {
					String title = ChordHelperApplet.VersionInfo.NAME;
					String message = "MIDIデバイス同士の接続に失敗しました。\n送信側(Tx):"+txm+" → 受信側(Rx):"+rxm+"\n\n" + ex;
					JOptionPane.showMessageDialog(null, message, title, JOptionPane.ERROR_MESSAGE);
				}
			});
		});
	}
	/**
	 * MIDIデバイスリストを最新の状態に更新し、このモデルを参照しているビューに通知します。
	 *
	 * <p>USB-MIDIデバイスが着脱されたときにこのメソッドを呼び出すと、
	 * 新しく装着されたMIDIデバイスを開くことができるようになります。
	 * 同時に、取り外されたMIDIデバイスが閉じられ、そのデバイスの表示も消えます。
	 * </p>
	 * <p>USB端子からMIDIデバイスを抜いた場合、そのMIDIデバイスの
	 * {@link Transmitter} や {@link Receiver}
	 * を接続したままで
	 * {@link MidiSystem#getMidiDeviceInfo()} を呼び出すと Java VM がクラッシュしてしまいます。
	 * これを避けるため、最初に{@link #disconnectAllDevices()}で接続をすべて切断してから
	 * MIDIデバイスリストを最新の状態に更新し、その後{@link #connectDevices(Map)}で接続を復元します。
	 * </p>
	 */
	public void update() {
		Map<MidiDeviceModel, Collection<MidiDeviceModel>> saved = disconnectAllDevices();
		List<MidiDevice.Info> newDeviceInfo = new ArrayList<>(getMidiDeviceInfo());
		Collection<MidiDeviceModel> oldDeviceModels = stream().filter(model->{
			MidiDevice device = model.getMidiDevice();
			if( device instanceof VirtualMidiDevice ) return false;
			if( newDeviceInfo.remove(device.getDeviceInfo()) ) return false;
			device.close(); return true;
		}).collect(Collectors.toSet());
		if( deviceModelList.removeAll(oldDeviceModels) ) {
			oldDeviceModels.forEach(m->deviceModelTree.get(m.getInOutType()).remove(m));
			saved.keySet().removeAll(oldDeviceModels); // Rx
			saved.values().forEach(m->m.removeAll(oldDeviceModels)); // Tx
		}
		newDeviceInfo.forEach(info->add(getMidiDevice(info)));
		connectDevices(saved);
		fireTreeStructureChanged(this, null, null, null);
	}

}
