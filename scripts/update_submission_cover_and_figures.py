from pathlib import Path

import win32com.client
from win32com.client import constants


DOCX_PATH = Path(r"D:/zjf20/Downloads/毕业设计（论文）-提交版.docx")

FIGURES = [
    ("图4-1 系统总体架构图", Path(r"D:/JAVA/ideaProjects/cet46-vocab/docs/thesis-assets/diagrams/system-architecture.png"), 15.5),
    ("图4-2 系统功能模块图", Path(r"D:/JAVA/ideaProjects/cet46-vocab/docs/thesis-assets/diagrams/functional-modules.png"), 15.5),
    ("图4-3 系统 E-R 图", Path(r"D:/JAVA/ideaProjects/cet46-vocab/docs/thesis-assets/diagrams/er-diagram.png"), 15.5),
    ("图6-3 系统部署流程图", Path(r"D:/JAVA/ideaProjects/cet46-vocab/docs/thesis-assets/diagrams/deployment-flow.png"), 14.5),
]

COVER_ROWS = [
    ("学    院", "计算机工程学院/大数据学院"),
    ("专业班级", "2022软件工程4班"),
    ("学生姓名", ""),
    ("学生学号", ""),
    ("指导教师", ""),
    ("提交日期", "年    月    日"),
]


def find_range(doc, text):
    for i in range(1, doc.Paragraphs.Count + 1):
        paragraph = doc.Paragraphs(i).Range
        if paragraph.Text.replace("\r", "").strip() == text:
            return paragraph
    raise RuntimeError(f"Text not found: {text}")


def cm_to_pt(value):
    return value * 28.3464567


def replace_cover_lines(doc):
    start = doc.Paragraphs(12).Range.Start
    end = doc.Paragraphs(17).Range.End
    rng = doc.Range(Start=start, End=end)
    rng.Text = ""
    table = doc.Tables.Add(rng, len(COVER_ROWS), 2)
    table.AllowAutoFit = False
    table.Rows.Alignment = constants.wdAlignRowCenter
    table.Range.Font.NameFarEast = "宋体"
    table.Range.Font.Name = "Times New Roman"
    table.Range.Font.Size = 12

    # remove all borders first
    for border_id in range(1, 7):
        table.Borders(border_id).LineStyle = constants.wdLineStyleNone

    left_width = cm_to_pt(4.2)
    right_width = cm_to_pt(8.6)

    for idx, (label, value) in enumerate(COVER_ROWS, start=1):
        left = table.Cell(idx, 1)
        right = table.Cell(idx, 2)
        left.Width = left_width
        right.Width = right_width
        left.Range.Text = label
        right.Range.Text = value
        left.Range.Font.NameFarEast = "宋体"
        left.Range.Font.Name = "Times New Roman"
        right.Range.Font.NameFarEast = "宋体"
        right.Range.Font.Name = "Times New Roman"
        left.Range.Font.Size = 12
        right.Range.Font.Size = 12
        left.Range.ParagraphFormat.Alignment = constants.wdAlignParagraphLeft
        right.Range.ParagraphFormat.Alignment = constants.wdAlignParagraphLeft
        left.VerticalAlignment = constants.wdCellAlignVerticalCenter
        right.VerticalAlignment = constants.wdCellAlignVerticalCenter
        right.Borders(constants.wdBorderBottom).LineStyle = constants.wdLineStyleSingle
        right.Borders(constants.wdBorderBottom).LineWidth = constants.wdLineWidth050pt

    table.Rows.SetLeftIndent(0, constants.wdAdjustNone)
    for row in table.Rows:
        row.HeightRule = constants.wdRowHeightAtLeast
        row.Height = cm_to_pt(0.82)


def replace_figure(doc, word, caption_text, image_path: Path, width_cm: float):
    caption_rng = find_range(doc, caption_text)
    caption_start = caption_rng.Start

    nearest = None
    nearest_distance = None
    for i in range(1, doc.InlineShapes.Count + 1):
        shp = doc.InlineShapes(i)
        if shp.Range.Start < caption_start:
            distance = caption_start - shp.Range.Start
            if distance < 2000 and (nearest_distance is None or distance < nearest_distance):
                nearest = shp
                nearest_distance = distance

    if nearest is not None:
        nearest.Range.Delete()

    selection = word.Selection
    selection.SetRange(caption_start, caption_start)
    inline = selection.InlineShapes.AddPicture(str(image_path), False, True)
    inline.LockAspectRatio = True
    inline.Width = cm_to_pt(width_cm)
    inline.Range.ParagraphFormat.Alignment = constants.wdAlignParagraphCenter
    selection.TypeParagraph()


def main():
    if not DOCX_PATH.exists():
        raise FileNotFoundError(DOCX_PATH)
    word = win32com.client.gencache.EnsureDispatch("Word.Application")
    word.Visible = False
    word.DisplayAlerts = 0
    try:
        doc = word.Documents.Open(str(DOCX_PATH))
        replace_cover_lines(doc)
        for caption, image_path, width_cm in FIGURES:
            replace_figure(doc, word, caption, image_path, width_cm)
        doc.Save()
        doc.Close(False)
        print(DOCX_PATH)
    finally:
        word.Quit()


if __name__ == "__main__":
    main()
