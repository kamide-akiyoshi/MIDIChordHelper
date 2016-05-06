package camidion.chordhelper.mididevice;

import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetAdapter;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.beans.PropertyVetoException;

import javax.sound.midi.MidiUnavailableException;
import javax.swing.JDesktopPane;
import javax.swing.JInternalFrame;
import javax.swing.JLayeredPane;
import javax.swing.JOptionPane;
import javax.swing.Timer;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;

/**
 * 開いている MIDI デバイスを置くためのデスクトップビュー
 */
public class MidiOpenedDevicesView extends JDesktopPane {

	/**
	 * ツリー上で選択状態が変わったとき、
	 * このデスクトップ上のフレームの選択状態に反映するためのリスナー
	 */
	private TreeSelectionListener treeSelectionListener = new TreeSelectionListener() {
		@Override
		public void valueChanged(TreeSelectionEvent e) {
			Object lastSelected = e.getNewLeadSelectionPath().getLastPathComponent();
			if( lastSelected instanceof MidiConnecterListModel ) {
				MidiConnecterListModel deviceModel = (MidiConnecterListModel)lastSelected;
				if( deviceModel.getMidiDevice().isOpen() ) {
					// 開いているMIDIデバイスがツリー上で選択されたら
					// このデスクトップでも選択する
					try {
						getMidiDeviceFrameOf(deviceModel).setSelected(true);
					} catch( PropertyVetoException ex ) {
						ex.printStackTrace();
					}
					return;
				}
			}
			// 閉じているMIDIデバイス、またはMIDIデバイス以外のノードがツリー上で選択されたら
			// このデスクトップ上のMIDIデバイスフレームをすべて非選択にする
			MidiDeviceFrame selectedDeviceFrame = getSelectedMidiDeviceFrame();
			if( selectedDeviceFrame == null ) return;
			try {
				selectedDeviceFrame.setSelected(false);
			} catch( PropertyVetoException ex ) {
				ex.printStackTrace();
			}
		}
	};

	/**
	 * ツリー表示からこのデスクトップにドロップされたMIDIデバイスモデルに対応するフレームを表示するためのリスナー
	 */
	private DropTargetAdapter dropTargetListener = new DropTargetAdapter() {
		@Override
		public void dragEnter(DropTargetDragEvent dtde) {
			if( dtde.getTransferable().isDataFlavorSupported(MidiDeviceTreeView.TREE_MODEL_FLAVOR) ) {
				dtde.acceptDrag(DnDConstants.ACTION_COPY_OR_MOVE);
			}
		}
		@Override
		public void drop(DropTargetDropEvent dtde) {
			dtde.acceptDrop(DnDConstants.ACTION_COPY_OR_MOVE);
			try {
				int maskedBits = dtde.getDropAction() & DnDConstants.ACTION_COPY_OR_MOVE;
				if( maskedBits == 0 ) {
					dtde.dropComplete(false);
					return;
				}
				Object data = dtde.getTransferable().getTransferData(MidiDeviceTreeView.TREE_MODEL_FLAVOR);
				if( ! (data instanceof MidiConnecterListModel) ) {
					dtde.dropComplete(false);
					return;
				}
				MidiConnecterListModel deviceModel = (MidiConnecterListModel)data;
				try {
					deviceModel.openDevice();
				} catch( MidiUnavailableException e ) {
					//
					// デバイスを開くのに失敗した場合
					//
					//   例えば、「Microsort MIDI マッパー」と
					//   「Microsoft GS Wavetable SW Synth」を
					//   同時に開こうとするとここに来る。
					//
					String title = "Cannot open MIDI device";
					String message = "MIDIデバイス "+deviceModel+" はすでに使用中です。\n"
						+"他のデバイスが連動して開いていないか確認してください。\n\n"
						+ e.getMessage();
					dtde.dropComplete(false);
					JOptionPane.showMessageDialog(null, message, title, JOptionPane.ERROR_MESSAGE);
					return;
				}
				if( ! deviceModel.getMidiDevice().isOpen() ) {
					// 例外が出なかったにも関わらずデバイスが開かれていない場合
					String title = "Cannot open MIDI device";
					String message = "MIDIデバイス "+deviceModel+" はすでに使用中です。\n"
						+"他のデバイスが連動して開いていないか確認してください。";
					dtde.dropComplete(false);
					JOptionPane.showMessageDialog(null, message, title, JOptionPane.ERROR_MESSAGE);
					return;
				}
				dtde.dropComplete(true);
				//
				// デバイスが正常に開かれたことを確認できたら
				// ドロップした場所へフレームを配置して可視化する。
				//
				MidiDeviceFrame deviceFrame = getMidiDeviceFrameOf(deviceModel);
				deviceFrame.setLocation(dtde.getLocation());
				deviceFrame.setVisible(true);
			}
			catch (Exception ex) {
				ex.printStackTrace();
				dtde.dropComplete(false);
			}
		}
	};

	private MidiCablePane cablePane = new MidiCablePane(this);

	public MidiOpenedDevicesView(MidiDeviceTreeView deviceTree) {
		deviceTree.addTreeSelectionListener(treeSelectionListener);
		add(cablePane, JLayeredPane.PALETTE_LAYER);
		int openedFrameIndex = 0;
		MidiDeviceTreeModel treeModel = (MidiDeviceTreeModel)deviceTree.getModel();
		for( MidiConnecterListModel deviceModel : treeModel.deviceModelList ) {
			deviceModel.addListDataListener(cablePane.midiConnecterListDataListener);
			MidiDeviceFrame frame = new MidiDeviceFrame(deviceModel, cablePane);
			frame.setSize(250, 90);
			frame.addInternalFrameListener(deviceTree.midiDeviceFrameListener);
			add(frame);
			if( ! deviceModel.getMidiDevice().isOpen() ) continue;
			frame.setLocation( 10+(openedFrameIndex%2)*260, 10+openedFrameIndex*50 );
			frame.setVisible(true);
			openedFrameIndex++;
		}
		// このデスクトップがリサイズされたらケーブル面もリサイズする
		addComponentListener(new ComponentAdapter() {
			@Override
			public void componentResized(ComponentEvent e) { cablePane.setSize(getSize()); }
		});
		new DropTarget( this, DnDConstants.ACTION_COPY_OR_MOVE, dropTargetListener, true );
	}

	/**
	 * 指定されたMIDIデバイスモデルに対するMIDIデバイスフレームを返します。
	 *
	 * @param deviceModel MIDIデバイスモデル
	 * @return 対応するMIDIデバイスフレーム（ない場合 null）
	 */
	public MidiDeviceFrame getMidiDeviceFrameOf(MidiConnecterListModel deviceModel) {
		JInternalFrame[] frames = getAllFramesInLayer(JLayeredPane.DEFAULT_LAYER);
		for( JInternalFrame frame : frames ) {
			if( ! (frame instanceof MidiDeviceFrame) ) continue;
			MidiDeviceFrame deviceFrame = (MidiDeviceFrame)frame;
			if( deviceModel.equals(deviceFrame.getMidiConnecterListView().getModel()) ) {
				return deviceFrame;
			}
		}
		return null;
	}

	/**
	 * このビュー上にある現在アクティブな{@link MidiDeviceFrame}を返します。
	 * アクティブな{@link MidiDeviceFrame}がない場合は null を返します。
	 * @return 現在アクティブな{@link MidiDeviceFrame}、または null
	 */
	public MidiDeviceFrame getSelectedMidiDeviceFrame() {
		JInternalFrame frame = getSelectedFrame();
		return frame instanceof MidiDeviceFrame ? (MidiDeviceFrame)frame : null;
	}

	private boolean isTimerStarted;
	/**
	 * タイムスタンプを更新するタイマーを開始または停止します。
	 * @param toStart trueで開始、falseで停止
	 */
	public void setAllDeviceTimestampTimers(boolean toStart) {
		if( isTimerStarted == toStart ) return;
		isTimerStarted = toStart;
		JInternalFrame[] frames = getAllFramesInLayer(JLayeredPane.DEFAULT_LAYER);
		for( JInternalFrame frame : frames ) {
			if( ! (frame instanceof MidiDeviceFrame) ) continue;
			Timer timer = ((MidiDeviceFrame)frame).getTimer();
			if( toStart ) timer.start(); else timer.stop();
		}
	}
}
