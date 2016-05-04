package camidion.chordhelper.mididevice;

import java.awt.Component;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DragGestureEvent;
import java.awt.dnd.DragGestureListener;
import java.awt.dnd.DragSource;

import javax.swing.JTree;
import javax.swing.event.InternalFrameAdapter;
import javax.swing.event.InternalFrameEvent;
import javax.swing.event.InternalFrameListener;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.TreeModel;

/**
 * MIDIデバイスツリービュー
 */
public class MidiDeviceTreeView extends JTree
{
	/**
	 *	{@link MidiDeviceFrame} を閉じたことを検知して再描画するためのリスナー
	 */
	public final InternalFrameListener midiDeviceFrameListener = new InternalFrameAdapter() {
		@Override
		public void internalFrameClosing(InternalFrameEvent e) { repaint(); }
	};

	public static final DataFlavor TREE_MODEL_FLAVOR = new DataFlavor(TreeModel.class, "TreeModel");

	private Transferable draggingObject = new Transferable() {
		private DataFlavor flavors[] = {TREE_MODEL_FLAVOR};
		@Override
		public Object getTransferData(DataFlavor flavor) { return getLastSelectedPathComponent(); }
		@Override
		public DataFlavor[] getTransferDataFlavors() { return flavors; }
		@Override
		public boolean isDataFlavorSupported(DataFlavor flavor) {
			return flavor.equals(TREE_MODEL_FLAVOR);
		}
	};
	/**
	 * MIDIデバイスツリービューを構築します。
	 * @param model このビューにデータを提供するモデル
	 */
	public MidiDeviceTreeView(MidiDeviceTreeModel model) {
		super(model);
		(new DragSource()).createDefaultDragGestureRecognizer(
			this, DnDConstants.ACTION_COPY_OR_MOVE, new DragGestureListener() {
				@Override
				public void dragGestureRecognized(DragGestureEvent dge) {
					if( (dge.getDragAction() & DnDConstants.ACTION_COPY_OR_MOVE) != 0 ) {
						dge.startDrag(DragSource.DefaultMoveDrop, draggingObject, null);
					}
				}
			}
		);
		setCellRenderer(new DefaultTreeCellRenderer() {
			@Override
			public Component getTreeCellRendererComponent(JTree tree, Object value,
					boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus)
			{
				super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);
				if(leaf) {
					setIcon(MidiConnecterListView.MIDI_CONNECTER_ICON);
					setDisabledIcon(MidiConnecterListView.MIDI_CONNECTER_ICON);
					MidiConnecterListModel listModel = (MidiConnecterListModel)value;
					setEnabled( ! listModel.getMidiDevice().isOpen() );
				}
				return this;
			}
		});
	}
}