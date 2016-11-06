package camidion.chordhelper.chorddiagram;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.AdjustmentEvent;
import java.awt.event.AdjustmentListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.ComboBoxModel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollBar;
import javax.swing.JToggleButton;
import javax.swing.SwingConstants;

import camidion.chordhelper.ButtonIcon;
import camidion.chordhelper.ChordDisplayLabel;
import camidion.chordhelper.music.Chord;

/**
 * ChordDiagram class for MIDI Chord Helper
 *
 * @auther
 *	Copyright (C) 2004-2014 Akiyoshi Kamide
 *	http://www.yk.rim.or.jp/~kamide/music/chordhelper/
 */
public class ChordDiagram extends JPanel {
	/**
	 * コードダイヤグラムの対象楽器
	 */
	public static enum Instrument {
		/** ウクレレ  */
		Ukulele(Arrays.asList(9,4,0,7)), // AECG
		/** ギター */
		Guitar(Arrays.asList(4,11,7,2,9,4)); // EBGDAE
		private Instrument(List<Integer> defaultOpenNotes) {
			this.defaultOpenNotes = Collections.unmodifiableList(defaultOpenNotes);
		}
		/**
		 * デフォルトの開放弦の音階を表すノート番号リストを返します。
		 * このリストは書き換えできません。
		 *
		 * @return 開放弦の音階（固定値）を表すノート番号リスト
		 */
		public List<Integer> getDefaultOpenNotes() { return defaultOpenNotes; }
		private List<Integer> defaultOpenNotes;
		/**
		 * 開放弦の音階を表す、書き換え（チューニング）可能な
		 * ノート番号の配列を生成します。
		 *
		 * @return 開放弦の音階（デフォルト値）を表すノート番号の配列
		 */
		public int[] createTunableOpenNotes() {
			int[] r = new int[defaultOpenNotes.size()];
			int i=0;
			for(int note : defaultOpenNotes) r[i++] = note;
			return r;
		}
	}
	/**
	 * コードダイアグラムを構築します。
	 * @param capoValueModel カポ値選択コンボボックスのデータモデル
	 */
	public ChordDiagram(ComboBoxModel<Integer> capoValueModel) {
		capoSelecterView.valueSelecter.setModel(capoValueModel);
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		add(new JPanel() {
			{
				setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
				setOpaque(false);
				add(Box.createHorizontalStrut(2));
				add(recordTextButton);
				add(Box.createHorizontalStrut(2));
				add(capoSelecterView);
			}
		});
		add(Box.createHorizontalStrut(5));
		add(new JPanel() {
			{
				setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
				setOpaque(false);
				add(new JPanel() {
					{
						setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
						setOpaque(false);
						add(new JPanel() {
							{
								add(titleLabel);
								setOpaque(false);
								setAlignmentY((float)0);
							}
						});
						add(diagramDisplay);
						fretRangeScrollbar.setAlignmentY((float)1.0);
						add(fretRangeScrollbar);
						add(new JPanel() {
							{
								setOpaque(false);
								for(JRadioButton rb : instButtons.values())
									add(rb);
								setAlignmentY((float)1.0);
							}
						});
					}
				});
				add(variationScrollbar);
			}
		});
	}
	/**
	 * コードをテキストに記録するボタン
	 */
	public JToggleButton recordTextButton =
		new JToggleButton("REC", new ButtonIcon(ButtonIcon.REC_ICON)) {
			{
				setMargin(new Insets(0,0,0,0));
				setToolTipText("Record to text ON/OFF");
			}
		};
	/**
	 * コードダイアグラムのタイトルラベル
	 */
	public ChordDisplayLabel titleLabel =
		new ChordDisplayLabel("Chord Diagram",null,null) {
			{
				setHorizontalAlignment(SwingConstants.CENTER);
				setVerticalAlignment(SwingConstants.BOTTOM);
			}
		};
	/**
	 * コードダイアグラム表示部
	 */
	private ChordDiagramDisplay diagramDisplay =
		new ChordDiagramDisplay(Instrument.Ukulele) {
			{
				setOpaque(false);
				setPreferredSize(new Dimension(120,120));
			}
		};
	/**
	 * 対象楽器選択ボタンのマップ
	 */
	private Map<Instrument,JRadioButton> instButtons =
		new EnumMap<Instrument,JRadioButton>(Instrument.class) {
			{
				ButtonGroup buttonGroup = new ButtonGroup();
				for( final Instrument instrument : Instrument.values() ) {
					String label = instrument.toString();
					JRadioButton radioButton = new JRadioButton(label) {
						{
							setOpaque(false);
							addActionListener(new ActionListener() {
								@Override
								public void actionPerformed(ActionEvent e) {
									diagramDisplay.tune(instrument);
								}
							});
						}
					};
					buttonGroup.add(radioButton);
					put(instrument, radioButton);
				}
				get(Instrument.Ukulele).setSelected(true);
			}
		};

	private JScrollBar variationScrollbar = new JScrollBar(JScrollBar.VERTICAL) {
		{
			setModel(diagramDisplay.chordVariations.indexModel);
			addAdjustmentListener(
				new AdjustmentListener() {
					@Override
					public void adjustmentValueChanged(AdjustmentEvent e) {
						setToolTipText(diagramDisplay.chordVariations.getIndexDescription());
					}
				}
			);
		}
	};
	private JScrollBar fretRangeScrollbar = new JScrollBar(JScrollBar.HORIZONTAL) {
		{
			setModel(diagramDisplay.fretViewIndexModel);
			setBlockIncrement(diagramDisplay.fretViewIndexModel.getExtent());
		}
	};
	/**
	 * カポ位置選択コンボボックス
	 */
	public CapoSelecterView capoSelecterView = new CapoSelecterView() {
		{
			checkbox.addItemListener(new ItemListener() {
				@Override
				public void itemStateChanged(ItemEvent e) { clear(); }
			});
		}
	};
	@Override
	public void setBackground(Color bgColor) {
		super.setBackground(bgColor);
		if( diagramDisplay == null ) return;
		diagramDisplay.setBackground(bgColor);
		capoSelecterView.setBackground(bgColor);
		capoSelecterView.valueSelecter.setBackground(bgColor);
		variationScrollbar.setBackground(bgColor);
		fretRangeScrollbar.setBackground(bgColor);
	}
	/**
	 * コード（和音）をクリアします。
	 *
	 * <p>{@link #setChord(Chord)} の引数に null を指定して呼び出しているだけです。
	 * </p>
	 */
	public void clear() { setChord(null); }
	/**
	 * コード（和音）を設定します。
	 * 表示中でない場合、指定のコードに関係なく null が設定されます。
	 *
	 * @param chord コード（クリアする場合は null）
	 */
	public void setChord(Chord chord) {
		if( ! isVisible() ) chord = null;
		titleLabel.setChord(chord);
		diagramDisplay.setChord(chord);
	}
	/**
	 * 対象楽器を切り替えます。
	 * @param instrument 対象楽器
	 * @throws NullPointerException 対象楽器がnullの場合
	 */
	public void setTargetInstrument(Instrument instrument) {
		instButtons.get(Objects.requireNonNull(instrument)).doClick();
	}
}
