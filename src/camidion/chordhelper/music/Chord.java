package camidion.chordhelper.music;

import java.awt.Color;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Vector;

import javax.swing.JLabel;

/**
 * 和音（コード - musical chord）のクラス
 */
public class Chord implements Cloneable {
	/** コード構成音の順序に対応する色 */
	public static final Color NOTE_INDEX_COLORS[] = {
		Color.red,
		new Color(0x40,0x40,0xFF),
		Color.orange.darker(),
		new Color(0x20,0x99,0x00),
		Color.magenta,
		Color.orange,
		Color.green
	};
	/** 音程差の半音オフセットのインデックス */
	private static enum OffsetIndex {
		THIRD, FIFTH, SEVENTH, NINTH, ELEVENTH, THIRTEENTH
	}
	/** 音程差 */
	public static enum Interval {

		/** 長２度（major 2nd / sus2） */
		SUS2(2, OffsetIndex.THIRD, "sus2", "suspended 2nd"),
		/** 短３度または増２度 */
		MINOR(3, OffsetIndex.THIRD, "m", "minor"),
		/** 長３度 */
		MAJOR(4, OffsetIndex.THIRD, "", "major"),
		/** 完全４度（parfect 4th / sus4） */
		SUS4(5, OffsetIndex.THIRD, "sus4", "suspended 4th"),

		/** 減５度または増４度（トライトーン ＝ 三全音 ＝ 半オクターブ） */
		FLAT5(6, OffsetIndex.FIFTH, "-5", "flatted 5th"),
		/** 完全５度 */
		PARFECT5(7, OffsetIndex.FIFTH, "", "parfect 5th"),
		/** 増５度または短６度 */
		SHARP5(8, OffsetIndex.FIFTH, "+5", "sharped 5th"),

		/** 長６度または減７度 */
		SIXTH(9, OffsetIndex.SEVENTH, "6", "6th"),
		/** 短７度 */
		SEVENTH(10, OffsetIndex.SEVENTH, "7", "7th"),
		/** 長７度 */
		MAJOR_SEVENTH(11, OffsetIndex.SEVENTH, "M7", "major 7th"),

		/** 短９度（短２度の１オクターブ上） */
		FLAT9(13, OffsetIndex.NINTH, "-9", "flatted 9th"),
		/** 長９度（長２度の１オクターブ上） */
		NINTH(14, OffsetIndex.NINTH, "9", "9th"),
		/** 増９度（増２度の１オクターブ上） */
		SHARP9(15, OffsetIndex.NINTH, "+9", "sharped 9th"),

		/** 完全１１度（完全４度の１オクターブ上） */
		ELEVENTH(17, OffsetIndex.ELEVENTH, "11", "11th"),
		/** 増１１度（増４度の１オクターブ上） */
		SHARP11(18, OffsetIndex.ELEVENTH, "+11", "sharped 11th"),

		/** 短１３度（短６度の１オクターブ上） */
		FLAT13(20, OffsetIndex.THIRTEENTH, "-13", "flatted 13th"),
		/** 長１３度（長６度の１オクターブ上） */
		THIRTEENTH(21, OffsetIndex.THIRTEENTH, "13", "13th");

		private Interval(int chromaticOffset, OffsetIndex offsetIndex, String symbol, String description) {
			this.chromaticOffset = chromaticOffset;
			this.offsetIndex = offsetIndex;
			this.symbol = symbol;
			this.description = description;
		}
		/**
		 * 半音差を返します。
		 * @return 半音差
		 */
		public int getChromaticOffset() { return chromaticOffset; }
		private int chromaticOffset;
		/**
		 * 対応するインデックスを返します。
		 * @return 対応するインデックス
		 */
		public OffsetIndex getChromaticOffsetIndex() { return offsetIndex; }
		private OffsetIndex offsetIndex;
		/**
		 * コード名に使う略称を返します。
		 * @return 略称
		 */
		public String getSymbol() { return symbol; }
		private String symbol;
		/**
		 * コード用の説明を返します。
		 * @return 説明
		 */
		public String getDescription() { return description; }
		private String description;
	}
	/**
	 * コードネームの音名を除いた部分（サフィックス）を組み立てて返します。
	 * @return コードネームの音名を除いた部分
	 */
	public String symbolSuffix() {
		String suffix = "";
		Interval itv3rd = offsets.get(OffsetIndex.THIRD);
		Interval itv5th = offsets.get(OffsetIndex.FIFTH);
		Interval itv7th = offsets.get(OffsetIndex.SEVENTH);
		if( itv3rd == Interval.MINOR ) {
			suffix += itv3rd.getSymbol();
		}
		if( itv7th != null ) {
			suffix += itv7th.getSymbol();
		}
		if( Arrays.asList(Interval.SUS2, Interval.SUS4).contains(itv3rd) ) {
			suffix += itv3rd.getSymbol();
		}
		if( itv5th != Interval.PARFECT5 ) {
			suffix += itv5th.getSymbol();
		}
		Vector<String> inParen = new Vector<String>();
		for( OffsetIndex index : Arrays.asList(OffsetIndex.NINTH, OffsetIndex.ELEVENTH, OffsetIndex.THIRTEENTH) ) {
			Interval interval = offsets.get(index);
			if( interval != null ) inParen.add(interval.getSymbol());
		}
		if( ! inParen.isEmpty() ) suffix += "("+String.join(",",inParen)+")";
		String alias = symbolSuffixAliases.get(suffix);
		return alias == null ? suffix : alias;
	}
	private static final Map<String, String> symbolSuffixAliases = new HashMap<String, String>() {
		{
			put("m-5", "dim");
			put("+5", "aug");
			put("m6-5", "dim7");
			put("(9)", "add9");
			put("7(9)", "9");
			put("M7(9)", "M9");
			put("7+5", "aug7");
			put("m6-5(9)", "dim9");
		}
	};
	/**
	 * コードの説明のうち、音名を除いた部分を組み立てて返します。
	 * @return コード説明の音名を除いた部分
	 */
	public String nameSuffix() {
		String suffix = "";
		Interval itv3rd = offsets.get(OffsetIndex.THIRD);
		Interval itv5th = offsets.get(OffsetIndex.FIFTH);
		Interval itv7th = offsets.get(OffsetIndex.SEVENTH);
		if( itv3rd == Interval.MINOR ) {
			suffix += " " + itv3rd.getDescription();
		}
		if( itv7th != null ) {
			suffix += " " + itv7th.getDescription();
		}
		if( Arrays.asList(Interval.SUS2, Interval.SUS4).contains(itv3rd) ) {
			suffix += " " + itv3rd.getDescription();
		}
		if( itv5th != Interval.PARFECT5 ) {
			suffix += " " + itv5th.getDescription();
		}
		Vector<String> inParen = new Vector<String>();
		for( OffsetIndex index : Arrays.asList(OffsetIndex.NINTH, OffsetIndex.ELEVENTH, OffsetIndex.THIRTEENTH) ) {
			Interval interval = offsets.get(index);
			if( interval != null ) inParen.add(interval.getDescription());
		}
		if( ! inParen.isEmpty() ) suffix += "("+String.join(",",inParen)+")";
		String alias = nameSuffixAliases.get(suffix);
		return alias == null ? suffix : alias;
	}
	private static final Map<String, String> nameSuffixAliases = new HashMap<String, String>() {
		{
			put("", " "+Interval.MAJOR.getDescription());
			put(" minor flatted 5th", " diminished (triad)");
			put(" sharped 5th", " augumented");
			put(" minor 6th flatted 5th", " diminished 7th");
			put("(9th)", " additional 9th");
			put(" 7th(9th)", " 9th");
			put(" major 7th(9th)", " major 9th");
			put(" 7th sharped 5th", " augumented 7th");
			put(" minor 6th flatted 5th(9th)", " diminished 9th");
		}
	};

	/**
	 * 現在有効な構成音（ルート、ベースは除く）の音程（初期値はメジャーコードの構成音）
	 */
	private Map<OffsetIndex, Interval> offsets = new HashMap<OffsetIndex, Interval>() {
		private void set(Interval itv) { put(itv.getChromaticOffsetIndex(), itv); }
		{ set(Interval.MAJOR); set(Interval.PARFECT5); }
	};
	/**
	 * このコードのルート音
	 */
	private NoteSymbol rootNoteSymbol;
	/**
	 * このコードのベース音（ルート音と異なる場合は分数コードの分母）
	 */
	private NoteSymbol bassNoteSymbol;

	/**
	 * 指定されたルート音と構成音を持つコードを構築します。
	 * @param root ルート音（ベース音としても使う）
	 * @param itvs その他の構成音の音程
	 */
	public Chord(NoteSymbol root, Interval... itvs) { this(root, root, itvs); }
	/**
	 * 指定されたルート音、ベース音、構成音（可変個数）を持つコードを構築します。
	 * @param root ルート音
	 * @param bass ベース音
	 * @param itvs その他の構成音の音程
	 */
	public Chord(NoteSymbol root, NoteSymbol bass, Interval... itvs) {
		this(root, bass, Arrays.asList(itvs));
	}
	/**
	 * 指定されたルート音、ベース音、構成音（コレクション）を持つコードを構築します。
	 * @param root ルート音
	 * @param bass ベース音
	 * @param itvs その他の構成音の音程
	 */
	public Chord(NoteSymbol root, NoteSymbol bass, Collection<Interval> itvs) {
		rootNoteSymbol = root;
		bassNoteSymbol = bass;
		for(Interval itv : itvs) if(itv != null) set(itv);
	}
	/**
	 * 元のコードの構成音の一部を変更した新しいコードを構築します。
	 * @param chord 元のコード
	 * @param itvs ルート音、ベース音を除いた、変更したい構成音の音程
	 */
	public Chord(Chord original, Interval... itvs) {
		this(original.rootNoteSymbol, original.bassNoteSymbol);
		offsets = new HashMap<>(original.offsets);
		for(Interval itv : itvs) if(itv != null) set(itv);
	}
	/**
	 * 指定された調と同名のコードを構築します。
	 * @param key 調
	 */
	public Chord(Key key) {
		int keyCo5 = key.toCo5();
		if( key.majorMinor() == Key.MajorMinor.MINOR ) {
			keyCo5 += 3; set(Interval.MINOR);
		}
		bassNoteSymbol = rootNoteSymbol = new NoteSymbol(keyCo5);
	}
	/**
	 * コード名の文字列からコードを構築します。
	 * @param chordSymbol コード名の文字列
	 */
	public Chord(String chordSymbol) {
		//
		// 分数コードの分子と分母に分ける
		String parts[] = chordSymbol.trim().split("(/|on)");
		if( parts.length == 0 ) return;
		//
		// ルート音とベース音を設定
		rootNoteSymbol = new NoteSymbol(parts[0]);
		if( parts.length > 1 && ! parts[0].equals(parts[1]) ) {
			bassNoteSymbol = new NoteSymbol(parts[1]);
		} else {
			bassNoteSymbol = rootNoteSymbol;
		}
		// 先頭の音名はもういらないので削除
		String suffix = parts[0].replaceFirst("^[A-G][#bx]*","");
		//
		// () があれば、その中身を取り出す
		String suffixParts[] = suffix.split("[\\(\\)]");
		if( suffixParts.length == 0 ) return;
		String suffixParen = "";
		if( suffixParts.length > 1 ) {
			suffixParen = suffixParts[1];
			suffix = suffixParts[0];
		}
		// +5 -5 aug dim
		if( suffix.matches(".*(\\+5|aug|#5).*") ) set(Interval.FLAT5);
		else if( suffix.matches(".*(-5|dim|b5).*") ) set(Interval.SHARP5);
		//
		// 6 7 M7
		if( suffix.matches(".*(M7|maj7|M9|maj9).*") ) set(Interval.MAJOR_SEVENTH);
		else if( suffix.matches(".*(6|dim[79]).*") ) set(Interval.SIXTH);
		else if( suffix.matches(".*7.*") ) set(Interval.SEVENTH);
		//
		// minor sus4  （maj7 と間違えないように比較しつつ、mmaj7も解釈させる）
		if( suffix.matches(".*m.*") && ! suffix.matches(".*ma.*") || suffix.matches(".*mma.*") ) set(Interval.MINOR);
		else if( suffix.matches(".*sus4.*") ) set(Interval.SUS4);
		//
		// 9th の判定
		if( suffix.matches(".*9.*") ) {
			set(Interval.NINTH);
			if( ! suffix.matches(".*(add9|6|M9|maj9|dim9).*") ) set(Interval.SEVENTH);
		}
		else {
			// () の中を , で分ける
			for( String p : suffixParen.split(",") ) {
				if( p.matches("(\\+9|#9)") ) set(Interval.SHARP9);
				else if( p.matches("(-9|b9)") ) set(Interval.FLAT9);
				else if( p.matches("9") ) set(Interval.NINTH);

				if( p.matches("(\\+11|#11)") ) set(Interval.SHARP11);
				else if( p.matches("11") ) set(Interval.ELEVENTH);

				if( p.matches("(-13|b13)") ) set(Interval.FLAT13);
				else if( p.matches("13") ) set(Interval.THIRTEENTH);

				// -5 や +5 が () の中にあっても解釈できるようにする
				if( p.matches("(-5|b5)") ) set(Interval.FLAT5);
				else if( p.matches("(\\+5|#5)") ) set(Interval.SHARP5);
			}
		}
	}
	/**
	 * ルート音を返します。
	 * @return ルート音
	 */
	public NoteSymbol rootNoteSymbol() { return rootNoteSymbol; }
	/**
	 * ベース音を返します。分数コードの場合はルート音と異なります。
	 * @return ベース音
	 */
	public NoteSymbol bassNoteSymbol() { return bassNoteSymbol; }
	/**
	 * このコードの構成音を、ルート音からの音程の配列として返します。
	 * ルート音自身やベース音は含まれません。
	 * @return 音程の配列
	 */
	public Interval[] intervals() {
		return offsets.values().toArray(new Interval[offsets.size()]);
	}
	/**
	 * 指定した音程が設定されているか調べます。
	 * @param itv 音程
	 * @return 指定した音程が設定されていたらtrue
	 */
	public boolean isSet(Interval itv) {
		return itv.equals(offsets.get(itv.getChromaticOffsetIndex()));
	}
	/**
	 * コードの同一性を判定します。ルート音、ベース音の異名同音は異なるものとみなされます。
	 * @param anObject 比較対象
	 * @return 等しければtrue
	 */
	@Override
	public boolean equals(Object anObject) {
		if( anObject == this ) return true;
		if( anObject instanceof Chord ) {
			Chord another = (Chord) anObject;
			if( ! rootNoteSymbol.equals(another.rootNoteSymbol) ) return false;
			if( ! bassNoteSymbol.equals(another.bassNoteSymbol) ) return false;
			return offsets.equals(another.offsets);
		}
		return false;
	}
	@Override
	public int hashCode() { return toString().hashCode(); }
	/**
	 * ルート音、ベース音の異名同音を同じとみなしたうえで、コードの同一性を判定します。
	 * @param another 比較対象のコード
	 * @return 等しければtrue
	 */
	public boolean equalsEnharmonically(Chord another) {
		if( another == this ) return true;
		if( another == null ) return false;
		if( ! rootNoteSymbol.equalsEnharmonically(another.rootNoteSymbol) ) return false;
		if( ! bassNoteSymbol.equalsEnharmonically(another.bassNoteSymbol) ) return false;
		return offsets.equals(another.offsets);
	}
	/**
	 * コード構成音の数を返します。ルート音は含まれますが、ベース音は含まれません。
	 * @return コード構成音の数
	 */
	public int numberOfNotes() { return offsets.size() + 1; }
	/**
	 * 指定された位置にある構成音のノート番号を返します。
	 * @param index 位置（０をルート音とした構成音の順序）
	 * @return ノート番号（該当する音がない場合は -1）
	 */
	public int noteAt(int index) {
		int rootnote = rootNoteSymbol.toNoteNumber();
		if( index == 0 ) return rootnote;
		Interval itv;
		int i=0;
		for( OffsetIndex offsetIndex : OffsetIndex.values() )
			if( (itv = offsets.get(offsetIndex)) != null && ++i == index )
				return rootnote + itv.getChromaticOffset();
		return -1;
	}
	/**
	 * コード構成音を格納したノート番号の配列を返します（ベース音は含まれません）。
	 * 音域が指定された場合、その音域の範囲内に収まるように転回されます。
	 * @param range 音域（null可）
	 * @param key キー（null可）
	 * @return ノート番号の配列
	 */
	public int[] toNoteArray(Range range, Key key) {
		int rootnote = rootNoteSymbol.toNoteNumber();
		int ia[] = new int[numberOfNotes()];
		int i;
		ia[i=0] = rootnote;
		Interval itv;
		for( OffsetIndex offsetIndex : OffsetIndex.values() )
			if( (itv = offsets.get(offsetIndex)) != null )
				ia[++i] = rootnote + itv.getChromaticOffset();
		if( range != null ) range.invertNotesOf(ia, key);
		return ia;
	}
	/**
	 * MIDIノート番号が、コードの構成音の何番目（０＝ルート音）にあるかを表すインデックス値を返します。
	 * 構成音に該当しない場合は -1 を返します。ベース音は検索されません。
	 * @param noteNumber MIDIノート番号
	 * @return 構成音のインデックス値
	 */
	public int indexOf(int noteNumber) {
		int relativeNote = noteNumber - rootNoteSymbol.toNoteNumber();
		if( Music.mod12(relativeNote) == 0 ) return 0;
		Interval itv;
		int i=0;
		for( OffsetIndex offsetIndex : OffsetIndex.values() ) {
			if( (itv = offsets.get(offsetIndex)) != null ) {
				i++;
				if( Music.mod12(relativeNote - itv.getChromaticOffset()) == 0 )
					return i;
			}
		}
		return -1;
	}
	/**
	 * 指定したキーのスケールを外れた構成音がないか調べます。
	 * @param key 調べるキー
	 * @return スケールを外れている構成音がなければtrue
	 */
	public boolean isOnScaleIn(Key key) { return isOnScaleInKey(key.toCo5()); }
	private boolean isOnScaleInKey(int keyCo5) {
		int rootnote = rootNoteSymbol.toNoteNumber();
		if( ! Music.isOnScale(rootnote, keyCo5) ) return false;
		Interval itv;
		for( OffsetIndex offsetIndex : OffsetIndex.values() ) {
			if( (itv = offsets.get(offsetIndex)) == null ) continue;
			if( ! Music.isOnScale(rootnote + itv.getChromaticOffset(), keyCo5) ) return false;
		}
		return true;
	}
	/**
	 * C/Amの調に近いほうの♯、♭の表記で、移調したコードを返します。
	 * @param chromaticOffset 移調幅（半音単位）
	 * @return 移調した新しいコード（移調幅が０の場合は自分自身）
	 */
	public Chord transposedChord(int chromaticOffset) {
		return transposedChord(chromaticOffset, 0);
	}
	/**
	 * 指定された調に近いほうの♯、♭の表記で、移調したコードを返します。
	 * @param chromaticOffset 移調幅（半音単位）
	 * @param originalKey 基準とする調
	 * @return 移調した新しいコード（移調幅が０の場合は自分自身）
	 */
	public Chord transposedChord(int chromaticOffset, Key originalKey) {
		return transposedChord(chromaticOffset, originalKey.toCo5());
	}
	private Chord transposedChord(int chromaticOffset, int originalKeyCo5) {
		if( chromaticOffset == 0 ) return this;
		int offsetCo5 = Music.mod12(Music.reverseCo5(chromaticOffset));
		if( offsetCo5 > 6 ) offsetCo5 -= 12;
		int keyCo5 = originalKeyCo5 + offsetCo5;
		//
		int newRootCo5 = rootNoteSymbol.toCo5() + offsetCo5;
		int newBassCo5 = bassNoteSymbol.toCo5() + offsetCo5;
		if( keyCo5 > 6 ) {
			newRootCo5 -= 12;
			newBassCo5 -= 12;
		}
		else if( keyCo5 < -5 ) {
			newRootCo5 += 12;
			newBassCo5 += 12;
		}
		return new Chord(new NoteSymbol(newRootCo5), new NoteSymbol(newBassCo5), intervals());
	}

	/**
	 * この和音の文字列表現としてコード名を返します。
	 * @return この和音のコード名
	 */
	@Override
	public String toString() {
		String chordSymbol = rootNoteSymbol + symbolSuffix();
		if( ! rootNoteSymbol.equals(bassNoteSymbol) ) chordSymbol += "/" + bassNoteSymbol;
		return chordSymbol;
	}
	/**
	 * コード名を HTML で返します。
	 *
	 * Swing の {@link JLabel#setText(String)} は HTML で指定できるので、
	 * 文字の大きさに変化をつけることができます。
	 *
	 * @param colorName 色のHTML表現（色名または #RRGGBB 形式）
	 * @return コード名のHTML
	 */
	public String toHtmlString(String colorName) {
		String span = "<span style=\"font-size: 120%\">";
		String endSpan = "</span>";
		String root = rootNoteSymbol.toString();
		String formattedRoot = (root.length() == 1) ? root + span :
			root.replace("#",span+"<sup>#</sup>").
			replace("b",span+"<sup>b</sup>").
			replace("x",span+"<sup>x</sup>");
		String formattedBass = "";
		if( ! rootNoteSymbol.equals(bassNoteSymbol) ) {
			String bass = bassNoteSymbol.toString();
			formattedBass = (bass.length() == 1) ? bass + span :
				bass.replace("#",span+"<sup>#</sup>").
				replace("b",span+"<sup>b</sup>").
				replace("x",span+"<sup>x</sup>");
			formattedBass = "/" + formattedBass + endSpan;
		}
		String suffix = symbolSuffix().
			replace("-5","<sup>-5</sup>").
			replace("+5","<sup>+5</sup>");
		return
			"<html>" +
			"<span style=\"color: " + colorName + "; font-size: 170% ; white-space: nowrap ;\">" +
			formattedRoot + suffix + endSpan + formattedBass +
			"</span>" +
			"</html>" ;
	}
	/**
	 * コードの説明（英語）を返します。
	 * @return コードの説明（英語）
	 */
	public String toName() {
		String name = rootNoteSymbol.toStringIn(NoteSymbol.Language.NAME) + nameSuffix() ;
		if( ! rootNoteSymbol.equals(bassNoteSymbol) ) {
			name += " on " + bassNoteSymbol.toStringIn(NoteSymbol.Language.NAME);
		}
		return name;
	}

	/**
	 * このコードのクローンを作成します。
	 */
	@Override
	public Chord clone() {
		Chord newChord = new Chord(rootNoteSymbol, bassNoteSymbol);
		newChord.offsets = new HashMap<>(offsets);
		return newChord;
	}
	/**
	 * 指定した音程の構成音を設定します。
	 * @param itv 設定する音程
	 */
	public void set(Interval itv) {
		offsets.put(itv.getChromaticOffsetIndex(), itv);
	}
	/**
	 * 指定した音程の構成音をクリアします。
	 * @param itv クリアする音程
	 */
	public void clear(Interval itv) {
		offsets.remove(itv.getChromaticOffsetIndex());
	}
}