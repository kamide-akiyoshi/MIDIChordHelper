package camidion.chordhelper.midieditor;

import javax.sound.midi.MetaEventListener;
import javax.sound.midi.MetaMessage;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;

/**
 * 拍子選択ビュー
 */
public class TimeSignatureSelecter extends JPanel implements MetaEventListener {
	SpinnerNumberModel upperTimesigSpinnerModel = new SpinnerNumberModel(4, 1, 32, 1);
	private JSpinner upperTimesigSpinner = new JSpinner(
		upperTimesigSpinnerModel
	) {
		{
			setToolTipText("Time signature (upper digit) - 拍子の分子");
		}
	};
	JComboBox<String> lowerTimesigCombobox = new JComboBox<String>() {
		{
			setToolTipText("Time signature (lower digit) - 拍子の分母");
			for( int i=0; i<6; i++ ) addItem( "/" + (1<<i) );
			setSelectedIndex(2);
		}
	};
	private class SetValueRunnable implements Runnable {
		byte[] qpm;
		public SetValueRunnable(byte[] qpm) { this.qpm = qpm; }
		@Override
		public void run() { setValue(qpm);}
	}
	@Override
	public void meta(MetaMessage msg) {
		switch(msg.getType()) {
		case 0x58: // Time signature (4 bytes) - 拍子
			if( ! SwingUtilities.isEventDispatchThread() ) {
				SwingUtilities.invokeLater(new SetValueRunnable(msg.getData()));
				break;
			}
			setValue(msg.getData());
			break;
		}
	}
	private class TimeSignatureLabel extends JLabel {
		private byte upper = -1;
		private byte lower_index = -1;
		{
			setToolTipText("Time signature - 拍子");
		}
		public void setTimeSignature(byte upper, byte lower_index) {
			if( this.upper == upper && this.lower_index == lower_index ) {
				return;
			}
			setText("<html><font size=\"+1\">" + upper + "/" + (1 << lower_index) + "</font></html>");
		}
	}
	private TimeSignatureLabel timesigValueLabel = new TimeSignatureLabel();
	private boolean	editable;
	public TimeSignatureSelecter() {
		add(upperTimesigSpinner);
		add(lowerTimesigCombobox);
		add(timesigValueLabel);
		setEditable(true);
	}
	public void clear() {
		upperTimesigSpinnerModel.setValue(4);
		lowerTimesigCombobox.setSelectedIndex(2);
	}
	public int getUpperValue() {
		return upperTimesigSpinnerModel.getNumber().intValue();
	}
	public byte getUpperByte() {
		return upperTimesigSpinnerModel.getNumber().byteValue();
	}
	public int getLowerValueIndex() {
		return lowerTimesigCombobox.getSelectedIndex();
	}
	public byte getLowerByte() {
		return (byte)getLowerValueIndex();
	}
	public byte[] getByteArray() {
		byte[] data = new byte[4];
		data[0] = getUpperByte();
		data[1] = getLowerByte();
		data[2] = (byte)( 96 >> getLowerValueIndex() );
		data[3] = 8;
		return data;
	}
	public void setValue(byte upper, byte lowerIndex) {
		upperTimesigSpinnerModel.setValue( upper );
		lowerTimesigCombobox.setSelectedIndex( lowerIndex );
		timesigValueLabel.setTimeSignature( upper, lowerIndex );
	}
	public void setValue(byte[] data) {
		if(data == null)
			clear();
		else
			setValue(data[0], data[1]);
	}
	public boolean isEditable() { return editable; }
	public void setEditable( boolean editable ) {
		this.editable = editable;
		upperTimesigSpinner.setVisible(editable);
		lowerTimesigCombobox.setVisible(editable);
		timesigValueLabel.setVisible(!editable);
		if( !editable ) {
			timesigValueLabel.setTimeSignature(getUpperByte(), getLowerByte());
		}
	}
}