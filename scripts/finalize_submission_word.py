from pathlib import Path
import sys

import win32com.client
from win32com.client import constants


DEFAULT_DOCX_PATH = Path(r"D:/zjf20/Downloads/毕业设计（论文）-提交版.docx")

SCHOOL_HEADER = "广州城市理工学院本科毕业设计（论文）"
WD_COLLAPSE_START = 1
WD_SECTION_BREAK_NEXT_PAGE = 2


def find_heading_range(doc, text):
    rng = doc.Content
    find = rng.Find
    find.ClearFormatting()
    find.Text = text
    if find.Execute():
        return rng
    raise RuntimeError(f"Heading not found: {text}")


def insert_section_break_before(doc, text):
    rng = find_heading_range(doc, text)
    rng.Collapse(WD_COLLAPSE_START)
    rng.InsertBreak(WD_SECTION_BREAK_NEXT_PAGE)


def set_footer_page_numbers(section, fmt, restart=False, start=1):
    footer = section.Footers(constants.wdHeaderFooterPrimary)
    footer.LinkToPrevious = False
    footer.Range.ParagraphFormat.Alignment = constants.wdAlignParagraphCenter
    footer.Range.Text = ""
    page_numbers = footer.PageNumbers
    page_numbers.RestartNumberingAtSection = restart
    if restart:
        page_numbers.StartingNumber = start
    page_numbers.NumberStyle = fmt
    page_numbers.Add(constants.wdAlignParagraphCenter, True)
    footer.Range.Font.NameFarEast = "宋体"
    footer.Range.Font.Name = "Times New Roman"
    footer.Range.Font.Size = 10.5


def add_body_header(section):
    section.PageSetup.OddAndEvenPagesHeaderFooter = True

    odd_header = section.Headers(constants.wdHeaderFooterPrimary)
    odd_header.LinkToPrevious = False
    odd_header.Range.Text = ""
    odd_header.Range.Fields.Add(odd_header.Range, constants.wdFieldStyleRef, "Heading 1")
    odd_header.Range.ParagraphFormat.Alignment = constants.wdAlignParagraphCenter
    odd_header.Range.Font.NameFarEast = "宋体"
    odd_header.Range.Font.Name = "Times New Roman"
    odd_header.Range.Font.Size = 10.5

    even_header = section.Headers(constants.wdHeaderFooterEvenPages)
    even_header.LinkToPrevious = False
    even_header.Range.Text = SCHOOL_HEADER
    even_header.Range.ParagraphFormat.Alignment = constants.wdAlignParagraphCenter
    even_header.Range.Font.NameFarEast = "宋体"
    even_header.Range.Font.Name = "Times New Roman"
    even_header.Range.Font.Size = 10.5

    for hdr in [odd_header, even_header]:
        borders = hdr.Range.ParagraphFormat.Borders
        borders(constants.wdBorderBottom).LineStyle = constants.wdLineStyleSingle
        borders(constants.wdBorderBottom).LineWidth = constants.wdLineWidth150pt


def format_references(doc):
    start = find_heading_range(doc, "参考文献").Start
    end = find_heading_range(doc, "致  谢").Start
    rng = doc.Range(Start=start, End=end)
    paragraphs = rng.Paragraphs
    for p in paragraphs:
        text = p.Range.Text.strip()
        if not text or text == "参考文献":
            continue
        p.Range.Font.NameFarEast = "宋体"
        p.Range.Font.Name = "Times New Roman"
        p.Range.Font.Size = 10.5
        p.Format.LineSpacingRule = constants.wdLineSpaceExactly
        p.Format.LineSpacing = 20
        p.Format.FirstLineIndent = 0
        p.Format.LeftIndent = 0
        p.Format.CharacterUnitFirstLineIndent = 0
        p.Format.CharacterUnitLeftIndent = 0
        p.Format.CharacterUnitHangingIndent = 2


def main():
    docx_path = Path(sys.argv[1]) if len(sys.argv) > 1 else DEFAULT_DOCX_PATH
    pdf_path = Path(sys.argv[2]) if len(sys.argv) > 2 else docx_path.with_suffix(".pdf")
    if not docx_path.exists():
        raise FileNotFoundError(docx_path)

    word = win32com.client.gencache.EnsureDispatch("Word.Application")
    word.Visible = False
    word.DisplayAlerts = 0

    try:
        doc = word.Documents.Open(str(docx_path))

        insert_section_break_before(doc, "摘  要")
        insert_section_break_before(doc, "第一章 绪论")

        # Update section references after inserting breaks.
        sec1 = doc.Sections(1)
        sec2 = doc.Sections(2)
        sec3 = doc.Sections(3)

        # Section 1: cover and declarations, no headers or page numbers.
        for header_type in [constants.wdHeaderFooterPrimary, constants.wdHeaderFooterEvenPages, constants.wdHeaderFooterFirstPage]:
            sec1.Headers(header_type).Range.Text = ""
            sec1.Footers(header_type).Range.Text = ""

        # Section 2: abstract + TOC, Roman numerals.
        sec2.Footers(constants.wdHeaderFooterPrimary).LinkToPrevious = False
        sec2.Headers(constants.wdHeaderFooterPrimary).Range.Text = ""
        sec2.Headers(constants.wdHeaderFooterEvenPages).Range.Text = ""
        set_footer_page_numbers(sec2, constants.wdPageNumberStyleLowercaseRoman, restart=True, start=1)

        # Section 3: body, Arabic numerals.
        set_footer_page_numbers(sec3, constants.wdPageNumberStyleArabic, restart=True, start=1)
        add_body_header(sec3)

        format_references(doc)

        # Update TOC and fields.
        if doc.TablesOfContents.Count >= 1:
            doc.TablesOfContents(1).Update()
        doc.Fields.Update()

        doc.Save()
        doc.SaveAs(str(pdf_path), FileFormat=constants.wdFormatPDF)

        pages = doc.ComputeStatistics(constants.wdStatisticPages)
        print(f"DOCX={docx_path}")
        print(f"PDF={pdf_path}")
        print(f"PAGES={pages}")
        doc.Close(False)
    finally:
        word.Quit()


if __name__ == "__main__":
    main()
