package camidion.chordhelper.mididevice;

import java.awt.datatransfer.DataFlavor;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.beans.PropertyVetoException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.sound.midi.MidiUnavailableException;
import javax.swing.JDesktopPane;
import javax.swing.JLayeredPane;
import javax.swing.JOptionPane;
import javax.swing.TransferHandler;
import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeModelListener;
import javax.swing.tree.TreePath;

import camidion.chordhelper.ChordHelperApplet;

/**
 * 開いているMIDIデバイスを置くためのデスクトップビュー
 */
public class MidiDeviceDesktopPane extends JDesktopPane {
	/**
	 * MIDIデバイスモデルからフレームを割り出すためのマップ
	 */
	private Map<MidiDeviceModel, MidiDeviceFrame> frameOfModel = new HashMap<>();
	/**
	 * 指定されたMIDIデバイスモデルを表示しているフレームを選択します。
	 * nullを指定するとフレームの選択を解除します。
	 * @param deviceModel 対象のMIDIデバイスモデル
	 */
	private void setSelectedMidiDeviceModel(MidiDeviceModel deviceModel) {
		if( deviceModel != null ) {
			MidiDeviceFrame deviceFrame = frameOfModel.get(deviceModel);
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
		try {
			MidiDeviceFrame selectedFrame = (MidiDeviceFrame)getSelectedFrame();
			if( selectedFrame != null ) selectedFrame.setSelected(false);
		} catch( Exception ex ) {
			ex.printStackTrace();
		}
	}
	/**
	 * 指定されたツリーパスが、オープンされているMIDIデバイスモデルの場合、それを表示しているフレームを選択します。
	 * それ以外の場合、フレームの選択を解除します。
	 * @param treePath 対象ツリーパス
	 */
	public void setTreePath(TreePath treePath) {
		if( treePath != null ) {
			Object leaf = treePath.getLastPathComponent();
			if( leaf instanceof MidiDeviceModel && ((MidiDeviceModel)leaf).getMidiDevice().isOpen() ) {
				setSelectedMidiDeviceModel((MidiDeviceModel)leaf);
				return;
			}
		}
		setSelectedMidiDeviceModel(null);
	}

	public MidiDeviceDesktopPane(MidiDeviceTreeView deviceTreeView,
			MidiDeviceInfoPane deviceInfoPane)
	{
		MidiCablePane cablePane = new MidiCablePane(this);
		add(cablePane, JLayeredPane.PALETTE_LAYER);
		addComponentListener(new ComponentAdapter() {
			@Override
			public void componentResized(ComponentEvent e) {
				cablePane.setSize(getSize());
			}
			@Override
			public void componentShown(ComponentEvent e) {
				cablePane.repaint();
			}
		});
		MidiDeviceTreeModel deviceTreeModel = deviceTreeView.getModel();
		TreeModelListener treeModelListener = new TreeModelListener() {
			@Override
			public void treeNodesChanged(TreeModelEvent e) { }
			@Override
			public void treeNodesInserted(TreeModelEvent e) { }
			@Override
			public void treeNodesRemoved(TreeModelEvent e) { }
			/**
			 * デバイスツリーの変更に応じてフレームの削除や追加を行います。
			 * 起動時のフレーム追加だけでなく、
			 * USBからMIDIデバイスが着脱された場合のフレームの削除や追加にも使います。
			 * @param e デバイスツリーからのツリーモデルイベント
			 */
			@Override
			public void treeStructureChanged(TreeModelEvent e) {
				Set<MidiDeviceModel> removedUsbMidiDevices =
						frameOfModel.keySet().stream()
						.filter(dm-> ! deviceTreeModel.contains(dm))
						.collect(Collectors.toSet());
				removedUsbMidiDevices.stream()
					.map(dm->frameOfModel.remove(dm))
					.filter(Objects::nonNull)
					.forEach(f->remove(f));
				deviceTreeModel.stream()
					.filter(dm-> ! frameOfModel.containsKey(dm))
					.forEach(dm->{
						MidiDeviceFrame df;
						frameOfModel.put(dm, df = new MidiDeviceFrame(dm, cablePane));
						//
						// トランスミッタ、レシーバの接続変更時の描画予約
						Stream.of(dm.getTransmitterListModel(), dm.getReceiverListModel())
							.filter(Objects::nonNull)
							.forEach(lm->lm.addListDataListener(cablePane.midiConnecterListDataListener));
						//
						// フレーム開閉時の描画予約
						Stream.of(
								cablePane.midiDeviceFrameListener,
								deviceTreeView.midiDeviceFrameListener,
								deviceInfoPane.midiDeviceFrameListener
						).forEach(fl->df.addInternalFrameListener(fl));
						//
						// フレーム移動時、変形時の描画予約
						df.addComponentListener(cablePane.midiDeviceFrameComponentListener);
						//
						// フレームを追加
						add(df);
						if(dm.getMidiDevice().isOpen()) df.setVisible(true);
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
