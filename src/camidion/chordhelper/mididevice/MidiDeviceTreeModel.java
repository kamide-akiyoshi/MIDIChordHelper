package camidion.chordhelper.mididevice;

import java.util.AbstractCollection;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.Vector;
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
 * 仮想MIDIデバイスを含めたすべてのMIDIデバイスモデル{@link MidiDeviceModel}をツリー構造で管理するモデル。
 * 読み取り専用のMIDIデバイスリストとしても、
 * I/Oタイプで分類されたMIDIデバイスツリーモデルとしても参照できます。
 */
public class MidiDeviceTreeModel extends AbstractList<MidiDeviceModel> implements TreeModel {
	@Override
	public String toString() { return "MIDI devices"; }
	/**
	 * リスト本体
	 */
	protected List<MidiDeviceModel> deviceModelList = new ArrayList<>();
	@Override
	public int size() { return deviceModelList.size(); }
	@Override
	public MidiDeviceModel get(int index) { return deviceModelList.get(index); }
	/**
	 * このリストの内容を反映したツリー構造のマップ
	 */
	protected Map<MidiDeviceInOutType, List<MidiDeviceModel>> deviceModelTree
		= Arrays.stream(MidiDeviceInOutType.values())
		.collect(Collectors.toMap(Function.identity(), t -> new ArrayList<>()));
	/**
	 * {@link AbstractList#add(E)}の操作を内部的に行います。
	 * 指定された要素をこのリストの最後に追加し、ツリー構造にも反映します。
	 * @param dm 追加するMIDIデバイスモデル
	 * @return true（{@link AbstractList#add(E)} と同様）
	 */
	protected boolean addInternally(MidiDeviceModel dm) {
		if( ! deviceModelList.add(dm) ) return false;
		deviceModelTree.get(dm.getInOutType()).add(dm);
		return true;
	}
	/**
	 * {@link AbstractCollection#removeAll(Collection)}の操作を内部的に行います。
	 * 指定されたコレクションに該当するすべての要素を、このリストから削除します。
	 * このリストが変更された場合、ツリー構造にも反映されます。
	 * @param c 削除する要素のコレクション
	 * @return このリストが変更された場合はtrue（{@link AbstractCollection#removeAll(Collection)} と同様）
	 */
	protected boolean removeAllInternally(Collection<?> c) {
		if( ! deviceModelList.removeAll(c) ) return false;
		c.stream().filter(o -> o instanceof MidiDeviceModel).forEach(
			o -> deviceModelTree.get(((MidiDeviceModel)o).getInOutType()).remove(o)
		);
		return true;
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

	/**
	 * {@link MidiSystem#getMidiDeviceInfo()} が返した配列を不変の {@link List} として返します。
	 *
	 * <p>注意点：MIDIデバイスをUSBから抜いて、他のデバイスとの接続を切断せずに
	 * {@link MidiSystem#getMidiDeviceInfo()}を呼び出すと
	 * （少なくとも Windows 10 で）Java VM がクラッシュすることがあります。
	 * </p>
	 * @return インストールされているMIDIデバイスの情報のリスト
	 */
	public static List<MidiDevice.Info> getMidiDeviceInfo() {
		return Arrays.asList(MidiSystem.getMidiDeviceInfo());
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
		// GUI
		MidiDeviceModel guiModel = new MidiDeviceModel(guiVirtualDevice, this);
		addInternally(guiModel);
		// シーケンサー
		try {
			addInternally(sequencerModel = new MidiSequencerModel(MidiSystem.getSequencer(false), this));
		} catch( MidiUnavailableException ex ) {
			String title = ChordHelperApplet.VersionInfo.NAME;
			String message = "MIDI sequencer unavailable\n" + ex;
			JOptionPane.showMessageDialog(null, message, title, JOptionPane.ERROR_MESSAGE);
		}
		// システムで使用可能な全MIDIデバイス（シーケンサーはすでに取得済みなので除外）
		for( MidiDevice device : getMidiDeviceInfo().stream().map(info -> {
			try {
				return MidiSystem.getMidiDevice(info);
			} catch( MidiUnavailableException ex ) {
				String title = ChordHelperApplet.VersionInfo.NAME;
				String message = "MIDI device '" + info + "' unavailable\n" + ex;
				JOptionPane.showMessageDialog(null, message, title, JOptionPane.ERROR_MESSAGE);
				return null;
			}
		}).filter(
			device -> Objects.nonNull(device) && ! (device instanceof Sequencer)
		).collect(Collectors.toList()) ) {
			if( device instanceof Synthesizer ) { // Java内蔵シンセサイザの場合
				try {
					addInternally(synthModel = new MidiDeviceModel(MidiSystem.getSynthesizer(), this));
				} catch( MidiUnavailableException ex ) {
					String title = ChordHelperApplet.VersionInfo.NAME;
					String message = "Java internal MIDI synthesizer unavailable\n" + ex;
					JOptionPane.showMessageDialog(null, message, title, JOptionPane.ERROR_MESSAGE);
				}
				continue;
			}
			MidiDeviceModel m = new MidiDeviceModel(device, this);
			//
			// 最初の MIDI OUT（Windowsの場合は通常、内蔵音源 Microsoft GS Wavetable SW Synth）
			if( firstMidiOutModel == null && m.getReceiverListModel() != null ) firstMidiOutModel = m;
			//
			// 最初の MIDI IN（USB MIDI インターフェースにつながったMIDIキーボードなど）
			if( firstMidiInModel == null && m.getTransmitterListModel() != null ) firstMidiInModel = m;
			//
			addInternally(m);
		}
		// MIDIデバイスモデルを開く。
		//
		//   NOTE: 必ず MIDI OUT Rx デバイスを先に開くこと。
		//
		//   そうすれば、後から開いた MIDI IN Tx デバイスからのタイムスタンプのほうが「若く」なるので、
		//   相手の MIDI OUT Rx デバイスは「信号が遅れてやってきた」と認識、遅れを取り戻そうとして
		//   即座に音を出してくれる。
		//
		//   開く順序が逆になると「進みすぎるから遅らせよう」として無用なレイテンシーが発生する原因になる。
		//
		List<MidiDeviceModel> openedDeviceModels = Stream.of(
			synthModel,
			firstMidiOutModel,
			sequencerModel,
			guiModel,
			firstMidiInModel
		).filter(Objects::nonNull).filter(dm->{
			try {
				dm.open();
				return true;
			} catch( MidiUnavailableException ex ) {
				String title = ChordHelperApplet.VersionInfo.NAME;
				String message = "Cannot open MIDI device '"+dm+"'\n"
					+ "MIDIデバイス "+dm+" を開くことができません。\n\n" + ex;
				JOptionPane.showMessageDialog(null, message, title, JOptionPane.ERROR_MESSAGE);
				return false;
			}
		}).collect(Collectors.toList());
		//
		// 開いたデバイスを相互に接続する。
		// 自身のTx/Rx同士の接続は、シーケンサーモデルはなし、それ以外（GUIデバイスモデル）はあり。
		connectDevices(
			openedDeviceModels.stream().filter(rxm->
				Objects.nonNull(rxm.getReceiverListModel())
			).collect(Collectors.toMap(Function.identity(), rxm->
				openedDeviceModels.stream().filter(txm->
					Objects.nonNull(txm.getTransmitterListModel())
					&& !(txm == sequencerModel && txm == rxm)
				).collect(Collectors.toList())
			))
		);
	}
	/**
	 * デバイス間の接続をすべて切断します。
	 * 各{@link Receiver}ごとに相手デバイスの{@link Transmitter}を閉じ、
	 * その時どのように接続されていたかを示すマップを返します。
	 *
	 * @return MIDIデバイスモデル接続マップ（再接続時に{@link #connectDevices(Map)}に指定可）
	 * <ul>
	 * <li>キー：各{@link Receiver}を持つMIDIデバイスモデル</li>
	 * <li>値：接続相手だった{@link Transmitter}を持つMIDIデバイスモデルのコレクション</li>
	 * </ul>
	 */
	public Map<MidiDeviceModel, Collection<MidiDeviceModel>> disconnectAllDevices() {
		Map<MidiDeviceModel, Collection<MidiDeviceModel>> rxToTxConnections = new LinkedHashMap<>();
		deviceModelList.stream().forEach(m -> {
			ReceiverListModel rxListModel = m.getReceiverListModel();
			if( rxListModel == null ) return;
			Collection<MidiDeviceModel> txDeviceModels = rxListModel.closeAllConnectedTransmitters();
			if( txDeviceModels.isEmpty() ) return;
			rxToTxConnections.put(m, txDeviceModels);
		});
		return rxToTxConnections;
	}
	/**
	 * デバイス間の接続を復元します。
	 *
	 * @param rxToTxConnections {@link #disconnectAllDevices()}が返したMIDIデバイスモデル接続マップ
	 * <ul>
	 * <li>キー：{@link Receiver}側デバイスモデル</li>
	 * <li>値：{@link Transmitter}側デバイスモデルのコレクション</li>
	 * </ul>
	 */
	public void connectDevices(Map<MidiDeviceModel, Collection<MidiDeviceModel>> rxToTxConnections) {
		rxToTxConnections.keySet().stream().forEach(rxm -> {
			Receiver rx = rxm.getReceiverListModel().getTransceivers().get(0);
			rxToTxConnections.get(rxm).stream().filter(Objects::nonNull).forEach(txm -> {
				try {
					txm.getTransmitterListModel().openTransmitter().setReceiver(rx);
				} catch( MidiUnavailableException e ) {
					e.printStackTrace();
				}
			});
		});
	}
	/**
	 * USB-MIDIデバイスの着脱後、MIDIデバイスリストを最新の状態に更新します。
	 *
	 * <p>USBからMIDIデバイスを抜いた場合に {@link #getMidiDeviceInfo()} で
	 * Java VM クラッシュが発生する現象を回避するため、更新前に全デバイスの接続を一時切断し、
	 * 更新完了後に接続を復元します。
	 * </p>
	 */
	public void updateMidiDeviceList() {
		// 一時切断
		Map<MidiDeviceModel, Collection<MidiDeviceModel>> rxToTxConnections = disconnectAllDevices();
		//
		// 追加・削除されたMIDIデバイスを特定
		List<MidiDevice.Info> toAdd = new Vector<>(getMidiDeviceInfo());
		List<MidiDeviceModel> toRemove = deviceModelList.stream().filter(m -> {
			MidiDevice d = m.getMidiDevice();
			if( d instanceof VirtualMidiDevice || toAdd.remove(d.getDeviceInfo()) ) return false;
			d.close(); return true;
		}).collect(Collectors.toList());
		// 削除されたデバイスのモデルを除去
		if( removeAllInternally(toRemove) ) {
			Set<MidiDeviceModel> rxModels = rxToTxConnections.keySet();
			rxModels.removeAll(toRemove);
			rxModels.forEach(m -> rxToTxConnections.get(m).removeAll(toRemove));
		}
		// 追加されたデバイスのモデルを登録
		toAdd.forEach(info -> {
			try {
				addInternally(new MidiDeviceModel(info, this));
			} catch( MidiUnavailableException e ) {
				e.printStackTrace();
			}
		});
		// 再接続
		connectDevices(rxToTxConnections);
		//
		// リスナーに通知してツリー表示を更新してもらう
		fireTreeStructureChanged(this, null, null, null);
	}
	/**
	 * {@link Transmitter}を持つすべてのデバイス（例：MIDIキーボードなど）について、
	 * {@link MidiDeviceModel#resetMicrosecondPosition()}でマイクロ秒位置をリセットします。
	 */
	public void resetMicrosecondPosition() {
		deviceModelList.stream().map(dm -> dm.getTransmitterListModel())
			.filter(Objects::nonNull)
			.forEach(tlm -> tlm.resetMicrosecondPosition());
	}

}
