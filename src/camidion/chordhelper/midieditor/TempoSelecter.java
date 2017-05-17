package camidion.chordhelper.midieditor;

import java.awt.Component;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;

import camidion.chordhelper.ButtonIcon;
import camidion.chordhelper.music.MIDISpec;

/**
 * テンポ選択（QPM: Quarter Per Minute）
 */
public class TempoSelecter extends JPanel {
	static final int DEFAULT_QPM = 120;
	protected SpinnerNumberModel tempoSpinnerModel =
		new SpinnerNumberModel(DEFAULT_QPM, 1, 999, 1);
	private JLabel tempoLabel = new JLabel(
		"=", new ButtonIcon(ButtonIcon.QUARTER_NOTE_ICON), JLabel.CENTER
	) {{
		setVerticalAlignment(JLabel.CENTER);
	}};
	private JLabel tempoValueLabel = new JLabel(""+DEFAULT_QPM);
	private JSpinner tempoSpinner = new JSpinner(tempoSpinnerModel);
	public TempoSelecter() {
		String tooltip = "Tempo in quatrers per minute - テンポ（１分あたりの四分音符の数）";
		tempoSpinner.setToolTipText(tooltip);
		tempoValueLabel.setToolTipText(tooltip);
		setLayout(new BoxLayout(this,BoxLayout.X_AXIS));
		add(tempoLabel);
		add(Box.createHorizontalStrut(5));
		add(tempoSpinner);
		add(tempoValueLabel);
		setEditable(true);
		tempoLabel.addMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent e) {
				Component obj = e.getComponent();
				if(obj == tempoLabel && isEditable()) {
					//
					// Adjust tempo by interval time between two clicks (tapping)
					//
					long currentMicrosecond = System.nanoTime()/1000;
					// midi_ch_selecter.noteOn( 9, 37, 100 );
					long interval_us = currentMicrosecond - prevBeatMicrosecondPosition;
					prevBeatMicrosecondPosition = currentMicrosecond;
					if( interval_us < 2000000L /* Shorter than 2 sec only */ ) {
						int tempo_in_bpm = (int)(240000000L / interval_us) >> 2; //  n/4拍子の場合のみを想定
					int old_tempo_in_bpm = getTempoInQpm();
					setTempo( ( tempo_in_bpm + old_tempo_in_bpm * 2 ) / 3 );
					}
				}
			}
		});
	}
	private long prevBeatMicrosecondPosition = 0;
	private boolean	editable;
	/**
	 * 編集可能かどうかを返します。
	 * @return 編集可能ならtrue
	 */
	public boolean isEditable() { return editable; }
	/**
	 * 編集可能かどうかを設定します。
	 * @param editable 編集可能ならtrue
	 */
	public void setEditable( boolean editable ) {
		this.editable = editable;
		tempoSpinner.setVisible( editable );
		tempoValueLabel.setVisible( !editable );
		if( !editable ) {
			// Copy spinner's value to label
			tempoValueLabel.setText(
				""+tempoSpinnerModel.getNumber().intValue()
			);
		}
		tempoLabel.setToolTipText(
			editable ?
			"Tap rhythmically here to adjust tempo - ここをクリックしてリズムをとるとテンポを合わせられます"
			: null
		);
	}
	/**
	 * テンポを返します。
	 * @return テンポ [BPM](QPM)
	 */
	public int getTempoInQpm() {
		return tempoSpinnerModel.getNumber().intValue();
	}
	/**
	 * テンポをMIDIメタメッセージのバイト列として返します。
	 * @return MIDIメタメッセージのバイト列
	 */
	public byte[] getTempoByteArray() {
		return MIDISpec.qpmTempoToByteArray(getTempoInQpm());
	}
	/**
	 * テンポを設定します。
	 * @param qpm BPM(QPM)の値
	 */
	public void setTempo(int qpm) {
		tempoSpinnerModel.setValue(new Integer(qpm));
		tempoValueLabel.setText(""+qpm);
	}
	/**
	 * MIDIメタメッセージのバイト列からテンポを設定します。
	 * @param msgdata MIDIメタメッセージのバイト列（null を指定した場合はデフォルトに戻る）
	 */
	public void setTempo(byte msgdata[]) {
		if( msgdata==null ) clear(); else setTempo(MIDISpec.byteArrayToQpmTempo(msgdata));
	}
	public void clear() { setTempo(DEFAULT_QPM); }
}
