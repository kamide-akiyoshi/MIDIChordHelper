package camidion.chordhelper.mididevice;

import java.awt.datatransfer.DataFlavor;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.beans.PropertyVetoException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import javax.sound.midi.MidiUnavailableException;
import javax.swing.JDesktopPane;
import javax.swing.JInternalFrame;
import javax.swing.JLayeredPane;
import javax.swing.JOptionPane;
import javax.swing.TransferHandler;
import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeModelListener;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.TreePath;

import camidion.chordhelper.ChordHelperApplet;

/**
 * 開いているMIDIデバイスを置くためのデスクトップビュー
 */
public class MidiDeviceDesktopPane extends JDesktopPane implements TreeSelectionListener {
	/**
	 * MIDIデバイスモデルからフレームを割り出すためのマップ
	 */
	private Map<MidiDeviceModel, MidiDeviceFrame> frameOfModel = new HashMap<>();
	/**
	 * MIDIデバイスツリービューで選択状態が変わったとき、
	 * このデスクトップで表示しているフレームの選択状態に反映します。
	 */
	@Override
	public void valueChanged(TreeSelectionEvent tse) {
		TreePath selectedTreePath = tse.getNewLeadSelectionPath();
		if( selectedTreePath != null ) {
			Object selectedLeaf = selectedTreePath.getLastPathComponent();
			if( selectedLeaf instanceof MidiDeviceModel ) {
				MidiDeviceModel selectedModel = (MidiDeviceModel)selectedLeaf;
				if( selectedModel.getMidiDevice().isOpen() ) {
					// 開いているMIDIデバイスがツリー上で選択されたら、対応するフレームを選択
					MidiDeviceFrame deviceFrame = frameOfModel.get(selectedModel);
					if( deviceFrame != null ) {
						deviceFrame.toFront();
						try {
							deviceFrame.setSelected(true);
						} catch( PropertyVetoException ex ) {
							ex.printStackTrace();
						}
						return;
					}
				}
			}
		}
		// それ以外が選択されたら、現在選択されているフレームを非選択
		JInternalFrame frame = getSelectedFrame();
		if( frame instanceof MidiDeviceFrame ) try {
			((MidiDeviceFrame)frame).setSelected(false);
		} catch( PropertyVetoException ex ) {
			ex.printStackTrace();
		}
	}

	public MidiDeviceDesktopPane(MidiDeviceTreeView deviceTreeView,
			MidiDeviceInfoPane deviceInfoPane, MidiDeviceDialog dialog)
	{
		MidiCablePane cablePane = new MidiCablePane(this);
		add(cablePane, JLayeredPane.PALETTE_LAYER);
		//
		// リサイズ時、表示時にMIDIケーブルを再描画
		addComponentListener(new ComponentAdapter() {
			@Override
			public void componentResized(ComponentEvent e) {
				cablePane.setSize(getSize());
				cablePane.repaint();
			}
			@Override
			public void componentShown(ComponentEvent e) { cablePane.repaint(); }
		});
		// デバイスツリーが変更されたときの更新処理を予約
		MidiDeviceTreeModel deviceTreeModel = deviceTreeView.getModel();
		TreeModelListener treeModelListener = new TreeModelListener() {
			@Override
			public void treeNodesChanged(TreeModelEvent e) { }
			@Override
			public void treeNodesInserted(TreeModelEvent e) { }
			@Override
			public void treeNodesRemoved(TreeModelEvent e) { }
			@Override
			public void treeStructureChanged(TreeModelEvent e) {
				// ツリーから削除されたMIDIデバイスモデルのフレームを削除
				frameOfModel.keySet().stream()
					.filter(m-> ! deviceTreeModel.contains(m))
					.collect(Collectors.toSet()).stream()
					.map(m-> frameOfModel.remove(m)).filter(Objects::nonNull)
					.forEach(f-> remove(f));
				//
				// ツリーに追加されたMIDIデバイスモデルのフレームを生成
				deviceTreeModel.stream()
					.filter(dm -> ! frameOfModel.containsKey(dm))
					.forEach(dm->{
						MidiDeviceFrame frame = new MidiDeviceFrame(dm, cablePane);
						frameOfModel.put(dm, frame);
						//
						// トランスミッタリストモデルが変化したときにMIDIケーブルを再描画
						TransmitterListModel txListModel = dm.getTransmitterListModel();
						if( txListModel != null ) txListModel.addListDataListener(cablePane.midiConnecterListDataListener);
						//
						// レシーバリストモデルが変化したときにMIDIケーブルを再描画
						ReceiverListModel rxListModel = dm.getReceiverListModel();
						if( rxListModel != null ) rxListModel.addListDataListener(cablePane.midiConnecterListDataListener);
						//
						// デバイスフレームが開閉したときの動作
						frame.addInternalFrameListener(cablePane.midiDeviceFrameListener);
						frame.addInternalFrameListener(deviceTreeView.midiDeviceFrameListener);
						frame.addInternalFrameListener(deviceInfoPane.midiDeviceFrameListener);
						//
						// 移動または変形時の動作
						frame.addComponentListener(cablePane.midiDeviceFrameComponentListener);
						//
						// サイズを設定したフレームをデスクトップに追加
						frame.setSize(250, dm.getInOutType() == MidiDeviceInOutType.MIDI_IN_OUT ? 90 : 70);
						add(frame);
						//
						// デバイスが開いていたら表示
						if( dm.getMidiDevice().isOpen() ) frame.setVisible(true);
					});
			}
		};
		deviceTreeModel.addTreeModelListener(treeModelListener);
		treeModelListener.treeStructureChanged(null);
		//
		// 表示したデバイスフレームを整列
		int toX = 10;
		int toY = 10;
		for( MidiDeviceModel deviceModel : deviceTreeModel ) {
			if( ! deviceModel.getMidiDevice().isOpen() ) continue;
			frameOfModel.get(deviceModel).setLocation(toX, toY);
			toX = (toX == 10 ? 270 : 10);
			toY += 70;
		}
		deviceTreeView.expandAll();
		//
		// ドロップ設定
		setTransferHandler(new TransferHandler() {
			@Override
			public boolean canImport(TransferSupport support) {
				if( ! support.isDrop() ) return false;
				if( support.isDataFlavorSupported(MidiDeviceTreeView.deviceModelFlavor) ) {
					// MIDIデバイスを開くためのドロップを受け付ける
					return true;
				}
				if( support.isDataFlavorSupported(TransmitterListView.transmitterFlavor) ) {
					cablePane.draggedOutOfDestination();
					// Transmitterの切り離しができるよう、ドロップを容認
					return true;
				}
				if( support.isDataFlavorSupported(ReceiverListView.receiverFlavor) ) {
					cablePane.draggedOutOfDestination();
					// Receiverはドロップ不可
				}
				return false;
			}
			@Override
			public boolean importData(TransferSupport support) {
				// canImport()がTransmitterを容認しているので、ここにTransmitterが来ることがある。
				// そこで、DataFlavorをチェックし、MIDIデバイスでなければ拒否する。
				DataFlavor flavor = MidiDeviceTreeView.deviceModelFlavor;
				if( ! support.isDataFlavorSupported(flavor) ) return false;
				MidiDeviceModel deviceModel = null;
				try {
					deviceModel = (MidiDeviceModel)support.getTransferable().getTransferData(flavor);
					MidiDeviceFrame deviceFrame = frameOfModel.get(deviceModel);
					if( deviceFrame == null ) return false;
					deviceModel.open();
					if( ! deviceModel.getMidiDevice().isOpen() ) {
						throw new MidiUnavailableException("開いたはずのMIDIデバイスが、開かれた状態になっていません。");
					}
					if( ! deviceFrame.isVisible() ) {
						deviceFrame.setLocation(support.getDropLocation().getDropPoint());
						deviceFrame.setVisible(true);
					}
					return true;
				} catch( MidiUnavailableException e ) {
					//
					// デバイスを開くのに失敗した場合
					//
					//   例えば、「Microsort MIDI マッパー」と「Microsoft GS Wavetable SW Synth」は
					//   連動して開かれるため、同時に開こうとすると、ここにたどり着く。
					//
					String title = ChordHelperApplet.VersionInfo.NAME;
					String message = "Cannot open MIDI device '"+deviceModel+"'"
							+ "\nMIDIデバイス "+deviceModel+" を開くことができません。\n"
						+"すでに他のデバイスが連動して開いている可能性があります。\n\n"
						+ e.getMessage();
					JOptionPane.showMessageDialog(null, message, title, JOptionPane.ERROR_MESSAGE);
				} catch (Exception ex) {
					ex.printStackTrace();
				}
				return false;
			}
		});
	}

}
