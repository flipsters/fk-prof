import React from "react";
import {Link} from "react-router";

const Header = props => {
  return (
    <div className="mdl-layout mdl-js-layout mdl-layout--fixed-header">
      <header className="mdl-layout__header">
        <div className="mdl-layout__header-row" style={{backgroundColor: props.isPolicyPage ? '#898984': 'rgb(63,81,181)'}}>
          <Link to={loc => ({ pathname: '/profiler', query: ''})} style={{ color: "white", textDecoration : "none"}}>
          <span className="mdl-layout-title" >Flipkart Profiler</span>
          </Link>
          <div className="mdl-layout-spacer"/>
          { !props.isPolicyPage &&
          <Link to={loc => ({pathname: '/profiler/policy', query: ''})}>
            <button className="mdl-button mdl-js-button mdl-button--icon" style={{color: "white"}}>
              <i className="material-icons">settings</i>
            </button>
          </Link>
          }
        </div>
      </header>
    </div>
  );
};
export default Header;
