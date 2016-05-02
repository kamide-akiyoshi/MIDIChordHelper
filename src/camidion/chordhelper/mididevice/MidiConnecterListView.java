package camidion.chordhelper.mididevice;

import java.awt.Component;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DragGestureEvent;
import java.awt.dnd.DragGestureListener;
import java.awt.dnd.DragSource;
import java.awt.dnd.DragSourceDragEvent;
import java.awt.dnd.DragSourceDropEvent;
import java.awt.dnd.DragSourceEvent;
import java.awt.dnd.DragSourceListener;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.dnd.DropTargetEvent;
import java.awt.dnd.DropTargetListener;

import javax.sound.midi.Receiver;
import javax.sound.midi.Transmitter;
import javax.swing.Icon;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;

import camidion.chordhelper.ButtonIcon;

/**
 * Transmitter(Tx)/Receiver(Rx) のリスト（view）
 *
 * <p>マウスで Tx からドラッグして Rx へドロップする機能を備えた
 * 仮想MIDI端子リストです。
 * </p>
 */
public class MidiConnecterListView extends JList<AutoCloseable>
	implements DragGestureListener, DragSourceListener, Transferable, DropTargetListener
{
	public static final Icon MIDI_CONNECTER_ICON =
		new ButtonIcon(ButtonIcon.MIDI_CONNECTOR_ICON);
	private class CellRenderer extends JLabel implements ListCellRenderer<AutoCloseable> {
		public Component getListCellRendererComponent(
			JList<? extends AutoCloseable> list,
			AutoCloseable value,
			int index,
			boolean isSelected,
			boolean cellHasFocus
		) {
			String text;
			if( value instanceof Receiver ) text = "Rx";
			else if( value instanceof Transmitter ) text = "Tx";
			else if( value == null ) text = null;
			else text = value.toString();
			setText(text);
			setIcon(MIDI_CONNECTER_ICON);
			if (isSelected) {
				setBackground(list.getSelectionBackground());
				setForeground(list.getSelectionForeground());
			} else {
				setBackground(list.getBackground());
				setForeground(list.getForeground());
			}
			setEnabled(list.isEnabled());
			setFont(list.getFont());
			setOpaque(true);
			return this;
		}
	}
	/**
	 * 仮想MIDI端子リストビューを生成します。
	 * @param model このビューから参照されるデータモデル
	 */
	public MidiConnecterListView(MidiConnecterListModel model) {
		super(model);
		setCellRenderer(new CellRenderer());
		setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		setLayoutOrientation(JList.HORIZONTAL_WRAP);
		setVisibleRowCount(0);
        (new DragSource()).createDefaultDragGestureRecognizer(
        	this, DnDConstants.ACTION_COPY_OR_MOVE, this
        );
		new DropTarget( this, DnDConstants.ACTION_COPY_OR_MOVE, this, true );
	}

	@Override
	public void dragGestureRecognized(DragGestureEvent dge) {
		if( (dge.getDragAction() & DnDConstants.ACTION_COPY_OR_MOVE) == 0 ) return;
		AutoCloseable e = getModel().getElementAt(locationToIndex(dge.getDragOrigin()));
		if( e instanceof Transmitter ) {
			if( e instanceof MidiConnecterListModel.NewTransmitter ) {
				transferringTx = getModel().getTransmitter();
			}
			else {
				transferringTx = (Transmitter)e;
			}
			dge.startDrag(DragSource.DefaultLinkDrop, this, this);
		}
	}

	@Override
	public void dragEnter(DragSourceDragEvent dsde) {}
	@Override
	public void dragOver(DragSourceDragEvent dsde) {}
	@Override
	public void dropActionChanged(DragSourceDragEvent dsde) {}
	@Override
	public void dragExit(DragSourceEvent dse) {}
	@Override
	public void dragDropEnd(DragSourceDropEvent dsde) {
		if( ! dsde.getDropSuccess() ) getModel().closeTransmitter(transferringTx);
		transferringTx = null;
	}

	private Transmitter transferringTx = null;
	private static final DataFlavor transmitterFlavor =
		new DataFlavor(Transmitter.class, "Transmitter");
	private static final DataFlavor transmitterFlavors[] = {transmitterFlavor};
	@Override
	public Object getTransferData(DataFlavor flavor) { return transferringTx; }
	@Override
	public DataFlavor[] getTransferDataFlavors() { return transmitterFlavors; }
	@Override
	public boolean isDataFlavorSupported(DataFlavor flavor) {
		return flavor.equals(transmitterFlavor);
	}

	@Override
	public void dragEnter(DropTargetDragEvent event) {
		if( event.isDataFlavorSupported(transmitterFlavor) )
			event.acceptDrag(DnDConstants.ACTION_COPY_OR_MOVE);
	}
	@Override
	public void dragExit(DropTargetEvent dte) {}
	@Override
	public void dragOver(DropTargetDragEvent dtde) {}
	@Override
	public void dropActionChanged(DropTargetDragEvent dtde) {}
	@Override
	public void drop(DropTargetDropEvent event) {
		event.acceptDrop(DnDConstants.ACTION_COPY_OR_MOVE);
		try {
			int maskedBits = event.getDropAction() & DnDConstants.ACTION_COPY_OR_MOVE;
			if( maskedBits == 0 || ! getModel().rxSupported() ) {
				event.dropComplete(false);
				return;
			}
			AutoCloseable destination = getModel().getElementAt(locationToIndex(event.getLocation()));
			if( ! (destination instanceof Receiver) ) {
				event.dropComplete(false);
				return;
			}
			Transmitter sourceTx = (Transmitter)event.getTransferable().getTransferData(transmitterFlavor);
			if( getModel().ConnectToReceiver(sourceTx, (Receiver)destination) == null ) {
				event.dropComplete(false);
				return;
			}
			event.dropComplete(true);
		}
		catch (Exception ex) {
			ex.printStackTrace();
			event.dropComplete(false);
		}
	}
	@Override
	public MidiConnecterListModel getModel() {
		return (MidiConnecterListModel)super.getModel();
	}
	/**
	 * 選択項目がトランスミッタの実体であれば、それを閉じます。
	 */
	public void closeSelectedTransmitter() {
		AutoCloseable ac = getSelectedValue();
		if( ac instanceof Transmitter )
			getModel().closeTransmitter((Transmitter)ac);
	}
}