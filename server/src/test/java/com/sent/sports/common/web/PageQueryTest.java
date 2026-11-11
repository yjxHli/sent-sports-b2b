package com.sent.sports.common.web;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PageQueryTest {

    @Test
    void defaultValuesAreNormalized() {
        PageQuery q = new PageQuery();
        assertThat(q.getPage()).isEqualTo(PageQuery.DEFAULT_PAGE);
        assertThat(q.getSize()).isEqualTo(PageQuery.DEFAULT_SIZE);
        assertThat(q.getSort()).isNull();
        assertThat(q.offset()).isZero();
        assertThat(q.limit()).isEqualTo(PageQuery.DEFAULT_SIZE);
    }

    @Test
    void pageBelowOneFallsBackToDefault() {
        PageQuery q = new PageQuery();
        q.setPage(0);
        assertThat(q.getPage()).isEqualTo(PageQuery.DEFAULT_PAGE);
        assertThat(q.offset()).isZero();
    }

    @Test
    void offsetComputesFromPageAndSize() {
        PageQuery q = new PageQuery();
        q.setPage(3);
        q.setSize(15);
        assertThat(q.getPage()).isEqualTo(3);
        assertThat(q.getSize()).isEqualTo(15);
        assertThat(q.offset()).isEqualTo(30);
        assertThat(q.limit()).isEqualTo(15);
    }

    @Test
    void sizeAboveMaxIsCapped() {
        PageQuery q = new PageQuery();
        q.setSize(500);
        assertThat(q.getSize()).isEqualTo(PageQuery.MAX_SIZE);
        assertThat(q.limit()).isEqualTo(PageQuery.MAX_SIZE);
    }

    @Test
    void sizeBelowOneIsRaisedToMinimum() {
        PageQuery q = new PageQuery();
        q.setSize(0);
        assertThat(q.getSize()).isEqualTo(1);
    }

    @Test
    void sortIsPassedThrough() {
        PageQuery q = new PageQuery();
        q.setSort("createdAt,desc");
        assertThat(q.getSort()).isEqualTo("createdAt,desc");
    }
}
