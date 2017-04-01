package camidion.chordhelper.mididevice;

import java.awt.Component;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;

import javax.swing.JComponent;
import javax.swing.JInternalFrame;
import javax.swing.JTree;
import javax.swing.ToolTipManager;
import javax.swing.TransferHandler;
import javax.swing.event.InternalFrameAdapter;
import javax.swing.event.InternalFrameEvent;
import javax.swing.event.InternalFrameListener;
import javax.swing.tree.DefaultTreeCellRenderer;

/**
 * MIDIデバイスツリービュー
 */
public class MidiDeviceTreeView extends JTree {
	public static final DataFlavor deviceModelFlavor  = new DataFlavor(MidiDeviceModel.class,"MidiDeviceModel");
	private static final DataFlavor flavors[] = {deviceModelFlavor};
	/**
	 *	{@link MidiDeviceFrame} が閉じられたり、選択されたりしたときに再描画するリスナー
	 */
	public final InternalFrameListener midiDeviceFrameListener = new InternalFrameAdapter() {
		@Override
		public void internalFrameActivated(InternalFrameEvent e) {
			JInternalFrame frame = e.getInternalFrame();
			if( ! (frame instanceof MidiDeviceFrame ) ) return;
			// 選択されたフレームのツリーノードを選択
			setSelectionPath(((MidiDeviceFrame)frame).getMidiDeviceModel().getTreePath());
		}
		@Override
		public void internalFrameClosing(InternalFrameEvent e) {
			// フレームが閉じようとするとき、閉じた後の再描画を予約
			repaint();
		}
	};
	@Override
	public MidiDeviceTreeModel getModel() { return (MidiDeviceTreeModel) super.getModel(); }
	/**
	 * ツリーノードを開き、ルートを選択した状態にします。
	 */
	public void expandAll() {
		for( int row = 0; row < getRowCount() ; row++ ) expandRow(row);
	}
	/**
	 * MIDIデバイスツリービューを構築します。
	 * @param model このビューにデータを提供するモデル
	 */
	public MidiDeviceTreeView(MidiDeviceTreeModel model) {
		super(model);
		setCellRenderer(new DefaultTreeCellRenderer() {
			@Override
			public Component getTreeCellRendererComponent(JTree tree, Object value,
					boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus)
			{
				super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);
				setToolTipText(value.toString());
				if(leaf) {
					MidiDeviceModel deviceModel = (MidiDeviceModel)value;
					if( deviceModel.getMidiDevice().isOpen() ) {
						setDisabledIcon(MidiDeviceDialog.MIDI_CONNECTER_ICON);
						setEnabled(false);
						setToolTipText(getToolTipText()+"はすでに開いています");
					} else {
						setIcon(MidiDeviceDialog.MIDI_CONNECTER_ICON);
						setEnabled(true);
						setToolTipText("ドラッグ＆ドロップで"+getToolTipText()+"が開きます");
					}
				}
				return this;
			}
		});
		// ツリーノードのToolTipを有効化
		ToolTipManager.sharedInstance().registerComponent(this);
		//
		// ドラッグを有効化
		setDragEnabled(true);
		setTransferHandler(new TransferHandler() {
			@Override
			public int getSourceActions(JComponent c) { return COPY_OR_MOVE; }
			@Override
			protected Transferable createTransferable(JComponent c) {
				JTree tree = (JTree) c;
				Object node = tree.getLastSelectedPathComponent();
				if( node instanceof MidiDeviceModel ) {
					MidiDeviceModel midiDeviceModel = (MidiDeviceModel)node;
					if( ! midiDeviceModel.getMidiDevice().isOpen() ) return new Transferable() {
						@Override
						public Object getTransferData(DataFlavor flavor) {
							return flavor.getRepresentationClass().isInstance(midiDeviceModel) ? midiDeviceModel : null;
						}
						@Override
						public DataFlavor[] getTransferDataFlavors() { return flavors; }
						@Override
						public boolean isDataFlavorSupported(DataFlavor flavor) {
							return flavors[0].equals(flavor);
						}
					};
				}
				return null;
			}
			@Override
			protected void exportDone(JComponent source, Transferable data, int action) {
				if( action != NONE ) repaint();
			}
		});
	}
}
