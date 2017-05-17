package camidion.chordhelper.midieditor;

import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;

/**
 * 拍子選択ビュー
 */
public class TimeSignatureSelecter extends JPanel {
	protected SpinnerNumberModel upperTimesigModel = new SpinnerNumberModel(4, 1, 32, 1);
	private JSpinner upperTimesigView = new JSpinner(upperTimesigModel) {
		{ setToolTipText("Time signature (upper digit) - 拍子の分子"); }
	};
	protected JComboBox<String> lowerTimesigView = new JComboBox<String>() {
		{
			setToolTipText("Time signature (lower digit) - 拍子の分母");
			for(int i=0; i<6; i++) addItem("/"+(1<<i)); setSelectedIndex(2);
		}
	};
	private static class TimeSignatureLabel extends JLabel {
		{ setToolTipText("Time signature - 拍子"); }
		private byte upper = -1;
		private byte lowerIndex = -1;
		public void setTimeSignature(byte upper, byte lowerIndex) {
			if( this.upper == upper && this.lowerIndex == lowerIndex ) return;
			setText("<html><font size=\"+1\">"+upper+"/"+(1<<lowerIndex)+"</font></html>");
			this.upper = upper;
			this.lowerIndex = lowerIndex;
		}
	}
	private TimeSignatureLabel timesigValueLabel = new TimeSignatureLabel();
	public TimeSignatureSelecter() {
		add(upperTimesigView);
		add(lowerTimesigView);
		add(timesigValueLabel);
		setEditable(true);
	}
	public byte[] getByteArray() {
		byte upper = upperTimesigModel.getNumber().byteValue();
		byte lowerIndex = (byte)lowerTimesigView.getSelectedIndex();
		return new byte[] { upper, lowerIndex, (byte)(96 >> lowerIndex), 8 };
	}
	protected void updateTimesigValueLabel() {
		timesigValueLabel.setTimeSignature(
			upperTimesigModel.getNumber().byteValue(),
			(byte)lowerTimesigView.getSelectedIndex()
		);
	}
	public void setValue(byte upper, byte lowerIndex) {
		upperTimesigModel.setValue(upper);
		lowerTimesigView.setSelectedIndex(lowerIndex);
		timesigValueLabel.setTimeSignature(upper, lowerIndex);
	}
	public void setValue(byte[] data) {
		if(data == null) clear(); else setValue(data[0], data[1]);
	}
	private boolean	editable;
	public boolean isEditable() { return editable; }
	public void setEditable(boolean editable) {
		this.editable = editable;
		upperTimesigView.setVisible(editable);
		lowerTimesigView.setVisible(editable);
		timesigValueLabel.setVisible(!editable);
		if(!editable) updateTimesigValueLabel();
	}
	public void clear() {
		upperTimesigModel.setValue(4);
		lowerTimesigView.setSelectedIndex(2);
		updateTimesigValueLabel();
	}
}
